package mixzpoker.lobby

import cats.effect.Sync
import cats.implicits._
import fs2.{Pull, Stream}
import fs2.concurrent.{Queue, Topic}
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

import mixzpoker.auth.AuthError
import mixzpoker.user.User
import mixzpoker.domain.lobby.{LobbyDto, LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyInputMessage._


class LobbyApi[F[_]: Sync: Logging](
  lobbyService: LobbyService[F],
  lobbyRepository: LobbyRepository[F],
  getAuthUser: String => F[Either[AuthError, User]]
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
  }

  private def lobbyWS(name: LobbyName): F[Response[F]] = {
    def processInput(queue: Queue[F, LobbyMessageContext], topic: Topic[F, LobbyOutputMessage])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, LobbyMessageContext] =
        stream.map { text =>
          decode[LobbyInputMessage](text).leftMap(_.toString)
        }.evalMap {
          case Left(err)   => info"$err".as(err.asLeft[LobbyInputMessage])
          case Right(msg)  => info"Event: Lobby=${name.value}, message=${msg.toString}".as(msg.asRight[String])
        }.collect {
          case Right(msg)  => LobbyMessageContext(user, name, msg)
        }

      val parsedWebSocketInput: Stream[F, LobbyMessageContext] = wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, LobbyMessageContext, Unit]
        case Some((token, stream)) => Pull.eval(getAuthUser(token).flatMap {_.fold(
          err =>
            topic.publish1(LobbyOutputMessage.ErrorMessage(s"unauthorized: ${err.toString}"))
              *> (Pull.done: Pull[F, LobbyMessageContext, Unit]).pure[F],
          user =>
            (processStreamInput(stream, user).pull.echo: Pull[F, LobbyMessageContext, Unit]).pure[F]
        )}).flatten
      }.stream

      (Stream.emits(Seq()) ++ parsedWebSocketInput).through(queue.enqueue)
    }

    for {
      _         <- lobbyRepository.ensureExists(name)
      toClient  =  lobbyService.topic.subscribe(1000).map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.queue, lobbyService.topic))
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
    lobby <- lobbyRepository.get(name)
    resp  <- Ok(lobby.dto.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req  <- request.decodeJson[LobbyDto.CreateLobbyRequest]
    _    <- lobbyRepository.create(req.name, user, req.gameType)
    resp <- Created()
  } yield resp
}

