package mixzpoker.lobby

import cats.data.EitherT
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.{AuthedRoutes, HttpRoutes, Request, Response}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.auth.{AuthError, AuthToken}
import mixzpoker.game.{EventId, GameId}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.user.User
import mixzpoker.domain.game.GameType
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.domain.lobby.{LobbyDto, LobbyInputMessage}
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.game.poker.PokerEvent.CreateGameEvent


class LobbyApi[F[_]: Sync: Logging](
  lobbyRepository: LobbyRepository[F], broker: Broker[F], lobbyService: LobbyService[F],
  getAuthUser: AuthToken => F[Either[AuthError, User]]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  object LobbyNameVar {
    def unapply(name: String): Option[LobbyName] = LobbyName.fromString(name).toOption
  }

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "lobby" / LobbyNameVar(name) / "ws" => lobbyWS(name)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case       GET  -> Root / "lobby"                      as user => getLobbies(user)
    case       GET  -> Root / "lobby" / LobbyNameVar(name) as user => getLobby(name)
    case req @ POST -> Root / "lobby" / "create"           as user => createLobby(req.req, user)
    case req @ POST -> Root / "lobby" / name / "start"     as user => startGame(req.req, name, user)
  }

  private def lobbyWS(name: LobbyName): F[Response[F]] = {
    def processInput(queue: Queue[F, LobbyEvent], userRef: Ref[F, Option[User]])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      val parsedWebSocketInput: Stream[F, LobbyEvent] = wsfStream.collect {
        case Text(text, _) => decode[LobbyInputMessage](text).leftMap(_.toString)
        case Close(_)      => "disconnected".asLeft[LobbyInputMessage]
      }.evalMap {
        case Left(err)     => info"$err".as(err.asLeft[LobbyInputMessage])
        case Right(msg)    => info"Event: Lobby=${name.value}, message=${msg.toString}".as(msg.asRight[String])
      }.collect {
        case Right(msg)    => msg
      }.evalMap[F, Option[LobbyEvent]] {
        case Register(token) =>
          (for {
            at   <- EitherT.fromEither[F]   (AuthToken.fromString(token))
            user <- EitherT                 (getAuthUser(at))
            _    <- EitherT.right[AuthError](userRef.update(_ => user.some))
          } yield ()).value.as(none[LobbyEvent])
        case msg @ _ => userRef.get.map(_.map(user => LobbyEvent(user, name, msg)))
      }.collect {
        case Some(msg) => msg
      }

      (Stream.emits(Seq()) ++ parsedWebSocketInput).through(queue.enqueue)
    }

    for {
      _         <- lobbyRepository.ensureExists(name)
      toClient  =  lobbyService.topic.subscribe(1000).map(msg => Text(msg.asJson.noSpaces))
      userRef   <- Ref.of(none[User])
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.queue, userRef))
    } yield ws
  }.recoverWith {
    case LobbyError.NoSuchLobby => NotFound()
    case _                      => InternalServerError()
  }

  private def getLobbies(user: User): F[Response[F]] = {
    //todo mb filter lobbies by user rights or smth
    // todo filter and pagination using query params
    for {
      _       <- info"Get lobbies req user:"
      lobbies <- lobbyRepository.list()
      _       <- info"Get lobbies: ${lobbies.map(_.dto).asJson.spaces2}"
      resp    <- Ok(lobbies.map(_.dto).asJson)
    } yield resp
  }

  private def getLobby(name: LobbyName): F[Response[F]] = for {
    lobby     <- lobbyRepository.get(name)
    resp      <- Ok(lobby.dto.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req  <- request.decodeJson[LobbyDto.CreateLobbyRequest]
    _    <- lobbyRepository.create(req.name, user, req.gameType)
    resp <- Created()
  } yield resp


  private def startGame(req: Request[F], name: String, user: User): F[Response[F]] = for {
    lobbyName <- LobbyName.fromString(name).liftTo[F]
    lobby     <- lobbyRepository.get(lobbyName)
    _         <- lobby.checkUserIsOwner(user).liftTo[F]
    gameId    <- lobby.gameType match {
                    case GameType.Poker => startPokerGame(lobby)
                  }
    resp      <- Ok(LobbyDto.CreateGameResponse(gameId.toString).asJson)
  } yield resp

  private def startPokerGame(lobby: Lobby): F[GameId] = for {
    queue <- broker.getQueue("poker-game-topic")
    gameId <- GameId.fromRandom
    eventId <- EventId.fromRandom
    event = CreateGameEvent(
      id = eventId,
      gameId = gameId,
      settings = lobby.gameSettings.asInstanceOf[PokerSettings], // todo asInstanceOf
      players = lobby.players.map(_.dto)
    )
    _ <- queue.enqueue1(event.asJson.noSpaces)
  } yield gameId
}

