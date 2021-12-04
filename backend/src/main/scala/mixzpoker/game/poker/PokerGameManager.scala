package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.syntax._
import tofu.logging.Logging
import tofu.syntax.logging._
import mixzpoker.domain.Token
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.game.poker.game.{PokerGame, PokerGameEvent}
import mixzpoker.game.poker.game.PokerGameEvent._
import mixzpoker.game.GameId
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.lobby.Player
import mixzpoker.user.UserId


// controls game's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def game: F[PokerGame]
  def id: GameId
  def processEvent(event: PokerGameEvent): F[Unit]
}

object PokerGameManager {
  def create[F[_]: Sync: Logging](
    gameId: GameId, settings: PokerSettings, players: List[Player], broker: Broker[F]
  ): F[PokerGameManager[F]] =
    for {
      _game   <- PokerGame.create(gameId, settings, players).liftTo[F]
      gameRef <- Ref.of[F, PokerGame](_game)
    } yield new PokerGameManager[F] {
      override def id: GameId = gameId
      override def game: F[PokerGame] = gameRef.get

      override def processEvent(event: PokerGameEvent): F[Unit] = for {
        _ <- info"processing an event ${event.asJson.spaces2}"
        g <- gameRef.get
        _ <- process(event)
      } yield ()

      private def process(event: PokerGameEvent): F[Unit] = {
        event match {
          case PlayerFold(userId) => playerFold(userId)
          case PlayerCheck(userId) => playerCheck(userId)
          case PlayerCall(userId, amount) => playerCall(userId, amount)
          case PlayerRaise(userId, amount) => playerRaise(userId, amount)
          case PlayerAllIn(userId, amount) => playerAllIn(userId, amount)
          case RoundStarts => EitherT.right[PokerError](().pure[F])
          case PreFlop => EitherT.right[PokerError](().pure[F])
          case Flop => EitherT.right[PokerError](().pure[F])
          case Turn => EitherT.right[PokerError](().pure[F])
          case River => EitherT.right[PokerError](().pure[F])
        }
      }.value.flatMap {
        case Left(err) => error"${err.toString}"
        case Right(_) => ().pure[F]
      }

      def playerFold(userId: UserId): EitherT[F, PokerError, Unit] = for {
        g <- EitherT.right[PokerError](game)
        _ <- EitherT.fromEither[F](g.playerFold(userId))
      } yield ()

      def playerCheck(userId: UserId): EitherT[F, PokerError, Unit] = for {
        g <- EitherT.right[PokerError](game)
        _ <- EitherT.fromEither[F](g.playerCheck(userId))
      } yield ()

      def playerCall(userId: UserId, amount: Token): EitherT[F, PokerError, Unit] = for {
        g <- EitherT.right[PokerError](game)
        _ <- EitherT.fromEither[F](g.playerCall(userId, amount))
      } yield ()

      def playerRaise(userId: UserId, amount: Token): EitherT[F, PokerError, Unit] = for {
        g <- EitherT.right[PokerError](game)
        _ <- EitherT.fromEither[F](g.playerRaise(userId, amount))
      } yield ()

      def playerAllIn(userId: UserId, amount: Token): EitherT[F, PokerError, Unit] = for {
        g <- EitherT.right[PokerError](game)
        _ <- EitherT.fromEither[F](g.playerAllIn(userId, amount))
      } yield ()
    }

}
