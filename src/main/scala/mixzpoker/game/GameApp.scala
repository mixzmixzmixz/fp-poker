package mixzpoker.game

import cats.data.EitherT
import cats.effect.Sync
//import cats.implicits._

import mixzpoker.game.GameError._
import mixzpoker.infrastructure.broker.Broker


trait GameApp[F[_]] {
  def run: F[Unit]

  def processMessage(event: GameEvent): F[Unit]
}

object GameApp {
  def of[F[_]: Sync](
    broker: Broker[F, GameEvent, String], gameRepository: GameRepository[F]
  ): F[ErrOr[GameApp[F]]] = {
    for {
      queue <- EitherT(broker.getQueue("gamesTopic"))
    } yield new GameApp[F] {
      override def run: F[Unit] =
        queue.dequeue.evalMap(processMessage).compile.drain

      override def processMessage(event: GameEvent): F[Unit] = {
        for {
          game <- EitherT(gameRepository.getGame(event.gameId))
          _ <- EitherT(game.processEvent(event))
        } yield ()
      }.leftMap {_ => ()}.merge
    }

  }.leftMap[GameError](GameBrokerError).value
}