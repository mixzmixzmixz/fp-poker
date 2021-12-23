package mixzpoker.game.poker

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.server.websocket.WebSocketBuilder
import io.circe.syntax._
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.auth.AuthService
import mixzpoker.domain.game.GameId
import mixzpoker.domain.user.User
import mixzpoker.domain.lobby.Lobby._
import mixzpoker.lobby.LobbyRepository


//todo pokerApp is going to be separate service with its own http api
// todo check user rights
class PokerApi[F[_]: Sync: Logging: GenUUID](
  pokerService: PokerService[F],
  lobbyRepository: LobbyRepository[F],
  authService: AuthService[F]
) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  object GameIdVar {
    def unapply(name: String): Option[GameId] = GameId.fromString(name).toOption
  }

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "poker" / GameIdVar(gameId: GameId) / "ws"          => pokerWS(gameId)
    case GET -> Root / "poker" / GameIdVar(gameId: GameId) / "chat" / "ws" => chatWS(gameId)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "poker" as user => getPokerGames(user)
  }

  private def pokerWS(gameId: GameId): F[Response[F]] =
    for {
      userRef <- Ref.of[F, Option[User]](None)
      toClient <- pokerService.toClient(gameId, userRef)
      fromClientPipe = pokerService.fromClientPipe(gameId)
      resp <- toClient.fold(NotFound()) { toClientStream =>
        WebSocketBuilder[F].build(
          toClientStream,
          _.through(authService.wsAuthPipe(userRef.some)).through(fromClientPipe)
        )
      }
    } yield resp

  private def chatWS(gameId: GameId): F[Response[F]] =
    pokerService
      .chatPipes(gameId)
      .flatMap {
        case Some((toClient, fromClient)) =>
          WebSocketBuilder[F].build(
            toClient,
            _.through(authService.wsAuthPipe()).through(fromClient)
          )
        case None => NotFound()
      }

  private def getPokerGames(user: User): F[Response[F]] = {
    // todo mb filter games by user rights or smth
    // todo filter and pagination using query params
    for {
      _       <- info"Get Poker Games"
      lobbies <- lobbyRepository.listWithGameStarted
      _       <- info"Get lobbies: ${lobbies.asJson.spaces2}"
      resp    <- Ok(lobbies.asJson)
    } yield resp
  }
}
