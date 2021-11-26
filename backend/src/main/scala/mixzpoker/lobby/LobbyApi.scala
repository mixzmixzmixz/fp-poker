package mixzpoker.lobby


import cats.effect.Sync
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
import org.http4s.{AuthedRoutes, Request, Response}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.game.poker.PokerEvent.CreateGameEvent
import mixzpoker.game.poker.PokerSettings
import mixzpoker.game.{EventId, GameId, GameType}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.messages.lobby.LobbyInputMessage
import mixzpoker.user.User


class LobbyApi[F[_]: Sync: Logging](
  lobbyRepository: LobbyRepository[F], broker: Broker[F], lobbyService: LobbyService[F]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {

    case GET -> Root / "lobby" as user => getLobbies(user)

    case GET -> Root / "lobby" / name as user => getLobby(name)

    case req @ POST -> Root / "lobby" / "create" as user =>  createLobby(req.req, user)

    case req @ POST -> Root / "lobby" / name / "join" as user => joinLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "leave" as user => leaveLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "start" as user => startGame(req.req, name, user)

    case GET -> Root / "lobby" / name / "ws" as user => lobbyWS(name, user)
  }

  private def lobbyWS(name: String, user: User): F[Response[F]] = {

    def processInput(queue: Queue[F, (LobbyInputMessage, User)])(wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {
      val parsedWebSocketInput: Stream[F, LobbyInputMessage] = wsfStream.collect {
        case Text(text, _) =>
          decode[LobbyInputMessage](text)
            .leftMap(e => LobbyInputMessage.InvalidMessage(e.toString))
            .merge

        case Close(_) => LobbyInputMessage.Disconnect
      }
      (Stream.emits(Seq()) ++ parsedWebSocketInput.map((_, user))).through(queue.enqueue)
    }

    for {
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      _         <- lobbyRepository.ensureExists(lobbyName)
      toClient  = lobbyService.topic.subscribe(1000).map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.queue))
    } yield ws
  }

  private def getLobbies(user: User): F[Response[F]] = {
    //todo mb filter lobbies by User rights or smth
    // todo filter and pagination using query params
    for {
      _       <- info"Get lobbies req user:"
      lobbies <- lobbyRepository.list()
      _       <- info"Get lobbies: ${lobbies.asJson.spaces2}"
      resp    <- Ok(lobbies.asJson)
    } yield resp
  }

  private def getLobby(name: String): F[Response[F]] = for {
    lobbyName <- LobbyName.fromString(name).liftTo[F]
    lobby     <- lobbyRepository.get(lobbyName)
    resp      <- Ok(lobby.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req  <- request.decodeJson[LobbyDto.CreateLobbyRequest]
    _    <- lobbyRepository.create(req.name, user, req.gameType)
    resp <- Created()
  } yield resp

  private def joinLobby(req: Request[F], name: String, user: User): F[Response[F]] = for {
    request   <- req.decodeJson[LobbyDto.JoinLobbyRequest]
    lobbyName <- LobbyName.fromString(name).liftTo[F]
    lobby     <- lobbyRepository.get(lobbyName)
    lobby2    <- lobby.joinPlayer(user, request.buyIn).liftTo[F]
    _         <- lobbyRepository.save(lobby2)
    resp      <- Ok()
  } yield resp

  private def leaveLobby(req: Request[F], name: String, user: User): F[Response[F]] = for {
    lobbyName <- LobbyName.fromString(name).liftTo[F]
    lobby <- lobbyRepository.get(lobbyName)
    lobby2 <- lobby.leavePlayer(user).liftTo[F]
    _ <- lobbyRepository.save(lobby2)
    resp <- Ok()
  } yield resp

  private def startGame(req: Request[F], name: String, user: User): F[Response[F]] = for {
    lobbyName <- LobbyName.fromString(name).liftTo[F]
    lobby <- lobbyRepository.get(lobbyName)
    _ <- lobby.checkUserIsOwner(user).liftTo[F]
    gameId <- lobby.gameType match {
      case GameType.Poker => startPokerGame(lobby)
    }
    resp <- Ok(LobbyDto.CreateGameResponse(gameId.toString).asJson)
  } yield resp

  private def startPokerGame(lobby: Lobby): F[GameId] = for {
    queue <- broker.getQueue("poker-game-topic")
    gameId <- GameId.fromRandom
    eventId <- EventId.fromRandom
    event = CreateGameEvent(
      id = eventId,
      gameId = gameId,
      settings = lobby.gameSettings.asInstanceOf[PokerSettings], // todo asInstanceOf
      users = lobby.users.map { case (user, token) => (user.id, token) }
    )
    _ <- queue.enqueue1(event.asJson.noSpaces)
  } yield gameId
}

