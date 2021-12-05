package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.Topic
import io.circe.syntax._
import tofu.logging.Logging
import tofu.syntax.logging._
import mixzpoker.domain.Token
import mixzpoker.domain.game.poker.{PokerEvent, PokerOutputMessage, PokerSettings}
import mixzpoker.domain.game.GameId
import mixzpoker.domain.user.UserId
import mixzpoker.game.poker.game.PokerGame
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.lobby.Player


// controls game's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def game: F[PokerGame]
  def topic: Topic[F, PokerOutputMessage]
  def id: GameId
  def processEvent(event: PokerEvent): F[PokerOutputMessage]
}

object PokerGameManager {
  def create[F[_]: Concurrent: Logging](
    gameId: GameId, settings: PokerSettings, players: List[Player], broker: Broker[F]
  ): F[PokerGameManager[F]] =
    for {
      _game   <- PokerGame.create(gameId, settings, players).liftTo[F]
      gameRef <- Ref.of[F, PokerGame](_game)
      _topic  <- Topic[F, PokerOutputMessage](PokerOutputMessage.ErrorMessage("hello there")) // todo correct init message
    } yield new PokerGameManager[F] {
      override def id: GameId = gameId
      override def game: F[PokerGame] = gameRef.get
      override def topic: Topic[F, PokerOutputMessage] = _topic

      override def processEvent(event: PokerEvent): F[PokerOutputMessage] = ???

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
