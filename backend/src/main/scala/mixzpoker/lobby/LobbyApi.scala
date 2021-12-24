package mixzpoker.lobby

import cats.effect.Sync
import cats.implicits._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.{AuthedRoutes, HttpRoutes, Request, Response}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.auth.AuthService
import mixzpoker.domain.lobby.{LobbyName, LobbyRequest}
import mixzpoker.domain.user.User


class LobbyApi[F[_]: Sync: Logging](
  lobbyService: LobbyService[F],
  lobbyRepository: LobbyRepository[F],
  authService: AuthService[F],
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  object LobbyNameVar {
    def unapply(name: String): Option[LobbyName] = LobbyName.fromString(name)
  }

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "lobby" / LobbyNameVar(name) / "ws"          => lobbyWS(name)
    case GET -> Root / "lobby" / LobbyNameVar(name) / "chat" / "ws" => chatWS(name)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case       GET  -> Root / "lobby"                      as user => getLobbies(user)
    case       GET  -> Root / "lobby" / LobbyNameVar(name) as user => getLobby(name)
    case req @ POST -> Root / "lobby" / "create"           as user => createLobby(req.req, user)
  }

  private def lobbyWS(name: LobbyName): F[Response[F]] =
    lobbyRepository.exists(name).flatMap {
      case false => NotFound()
      case true  =>
        val toClient       = lobbyService.toClient(name)
        val fromClientPipe = lobbyService.fromClientPipe(name)
        WebSocketBuilder[F].build(
          toClient,
          _.through(authService.wsAuthPipe()).through(fromClientPipe)
        )
    }

  private def chatWS(name: LobbyName): F[Response[F]] =
    lobbyService
      .chatPipes(name)
      .flatMap {
        case Some((toClient, fromClient)) =>
          WebSocketBuilder[F].build(
            toClient,
            _.through(authService.wsAuthPipe()).through(fromClient)
          )
        case None => NotFound()
      }

  private def getLobbies(user: User): F[Response[F]] = {
    //todo mb filter lobbies by user rights or smth
    // todo filter and pagination using query params
    for {
      lobbies <- lobbyRepository.list()
      resp    <- Ok(lobbies.asJson)
    } yield resp
  }

  private def getLobby(name: LobbyName): F[Response[F]] = for {
    lobby <- lobbyRepository.get(name)
    resp  <- Ok(lobby.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req     <- request.decodeJson[LobbyRequest.CreateLobbyRequest]
    created <- lobbyService.create(req.name, user, req.gameType)
    resp    <- if (created) Created() else Conflict()
  } yield resp
}

