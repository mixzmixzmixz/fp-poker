package mixzpoker.game.poker

import cats.data.EitherT
import cats.implicits._
import cats.effect.{Concurrent, Sync}
import org.http4s.{AuthedRoutes, Response}
import org.http4s.dsl.Http4sDsl
//import org.http4s.circe._
//import io.circe.syntax.EncoderOps

import mixzpoker.user.User
import mixzpoker.game.{GameId, GameRepository}


class PokerApi[F[_]: Sync: Concurrent](gameRepository: GameRepository[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "poker" / gameId / "game" as user =>
      getGameState(gameId)

    case req @ POST -> Root / "poker" / gameId / "game" as user => ???

    case req @ POST -> Root / "poker" / gameId / "join" as user => ???


  }

  private def getGameState(gameId: String): F[Response[F]] = {
    for {
      id <- EitherT(GameId.fromString(gameId).pure[F])
      game <- EitherT(gameRepository.getGame(id))
      resp = Ok()
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten
}
