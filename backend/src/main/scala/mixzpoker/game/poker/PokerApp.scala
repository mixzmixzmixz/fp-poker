package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.parser.decode
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.game.{GameError, GameId}
import mixzpoker.game.GameError._
import mixzpoker.game.poker.game.{PokerGame, PokerGameEvent}
import mixzpoker.infrastructure.broker.Broker
import PokerEvent._


trait PokerApp[F[_]] {
  def run: F[Unit]
  def processMessage(message: String): F[Unit]
  def getGame(gameId: GameId): EitherT[F, GameError, PokerGame]
}

object PokerApp {
  def of[F[_]: ConcurrentEffect: Logging](broker: Broker[F]): F[PokerApp[F]] = {
    for {
      pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])
    } yield new PokerApp[F] {

      override def run: F[Unit] = for {
        _ <- info"Run poker App!"
        q <- broker.getQueue("poker-game-topic")
        _ <- q.dequeue.evalMap(processMessage).compile.drain
      } yield ()

      override def processMessage(message: String): F[Unit] = {
        for {
          _ <- EitherT.right[GameError](info"Got msg: $message")
          event <- EitherT.fromEither[F](decode[PokerEvent](message).leftMap(err => DecodeError(err)))
          _ <- EitherT.right[GameError](info"Got event: ${event.toString}")
          _ <- event match {
            case GameEvent(_, gameId, event) => processGameEvent(event, gameId)
            case e: CreateGameEvent => createGame(e)
          }
          _ <- EitherT.right[GameError](info"Successfully proceed message")
        } yield ()
      }.value.flatMap(_.fold(err => error"${err.toString}", _ => ().pure[F]))

      override def getGame(gameId: GameId): EitherT[F, GameError, PokerGame] = for {
        manager <- EitherT(pokerManagers.get.map(_.get(gameId).toRight[GameError](NoSuchGame)))
        game <- EitherT.right(manager.game)
      } yield game

      private def processGameEvent(event: PokerGameEvent, gameId: GameId): EitherT[F, GameError, Unit] = for {
        manager <- EitherT(pokerManagers.get.map(_.get(gameId).toRight[GameError](NoSuchGame)))
        _ <- EitherT.right[GameError](manager.processEvent(event))
      } yield ()

      private def createGame(event: CreateGameEvent): EitherT[F, GameError, Unit] = for {
        gm <- PokerGameManager.create(event.gameId, event.settings, event.users, broker)
        _ <- EitherT.right[GameError](pokerManagers.update { _.updated(gm.id, gm) })
      } yield ()
    }
  }
}