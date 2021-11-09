package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.parser.decode

import mixzpoker.game.{GameError, GameId}
import mixzpoker.game.GameError._
import mixzpoker.game.poker.game.PokerGameEvent
import mixzpoker.infrastructure.broker.Broker
import PokerEvent._


trait PokerApp[F[_]] {
  def run: F[Unit]
  def processMessage(message: String): F[Unit]
}

object PokerApp {
  def of[F[_]: Sync](broker: Broker[F]): EitherT[F, GameError, PokerApp[F]] = {
    for {
      queue <- broker.getQueue("poker-game-topic").leftMap(GameBrokerError)
      pokerManagers <- EitherT.right[GameError](Ref.of(Map.empty[GameId, PokerGameManager[F]]))
    } yield new PokerApp[F] {

      override def run: F[Unit] =
        queue.dequeue.evalMap(processMessage).compile.drain

      override def processMessage(message: String): F[Unit] = {
        for {
          event <- EitherT.fromEither[F](decode[PokerEvent](message).leftMap(err => DecodeError(err)))
          _ <- event match {
            case GameEvent(_, gameId, event) => processGameEvent(event, gameId)
            case e: CreateGameEvent => createGame(e)
          }
        } yield ()
      }.leftMap {_ => ()}.merge // todo log errs

      private def processGameEvent(event: PokerGameEvent, gameId: GameId) = for {
        manager <- EitherT(pokerManagers.get.map(_.get(gameId).toRight[GameError](NoSuchGame)))
        _ <- EitherT.right[GameError](manager.processEvent(event))
      } yield ()

      private def createGame(event: CreateGameEvent) = for {
        gm <- PokerGameManager.create(event.settings, event.users, broker)
        _ <- EitherT.right[GameError](pokerManagers.update { _.updated(gm.id, gm) })
      } yield ()
    }
  }
}