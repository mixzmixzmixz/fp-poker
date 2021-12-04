package mixzpoker.game.poker

import cats.implicits._
import cats.effect.{Concurrent, Sync}
import mixzpoker.game.GameId
import mixzpoker.infrastructure.broker.Broker
import org.http4s.{AuthedRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax.EncoderOps

import mixzpoker.user.User

//todo pokerApp is going to be separate service with its own http api
// todo check user rights
class PokerApi[F[_]: Sync: Concurrent](broker: Broker[F], pokerApp: PokerService[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  object GameIdVar {
    def unapply(name: String): Option[GameId] = GameId.fromString(name).toOption
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case       GET  -> Root / "poker" / GameIdVar(gameId) / "game" as user => getGameState(gameId)

    case req @ POST -> Root / "poker" / GameIdVar(gameId) / "game" as user => ???

    case req @ POST -> Root / "poker" / GameIdVar(gameId) / "join" as user => ???

  }

  private def getGameState(gameId: GameId): F[Response[F]] = {
    for {
      game <- pokerApp.getGame(gameId)
      resp <- Ok(PokerDto.fromPokerGame(game).asJson)
    } yield resp
  }
}
