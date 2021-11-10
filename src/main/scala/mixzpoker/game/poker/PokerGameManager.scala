package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.syntax._
import mixzpoker.domain.Token
import mixzpoker.game.poker.game.{PokerGameEvent, PokerGame}
import mixzpoker.game.poker.game.PokerGameEvent._
import mixzpoker.game.{GameError, GameId}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.user.UserId


// controls game's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def game: F[PokerGame]
  def id: GameId
  def processEvent(event: PokerGameEvent): F[Unit]
}

object PokerGameManager {
  def create[F[_]: Sync](
    gameId: GameId, settings: PokerSettings, users: List[(UserId, Token)], broker: Broker[F]
  ): EitherT[F, GameError, PokerGameManager[F]] =
    for {
      _game <- EitherT.fromEither[F](PokerGame.create(gameId, settings, users))
      gameRef <- EitherT.right[GameError](Ref.of[F, PokerGame](_game))
    } yield new PokerGameManager[F] {
      override def id: GameId = gameId
      override def game: F[PokerGame] = gameRef.get

      override def processEvent(event: PokerGameEvent): F[Unit] = for {
        //logging
        _ <- Sync[F].delay(println(s"processing an event ${event.asJson}"))
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
        case Left(err) => Sync[F].delay(println(err.toString))
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
