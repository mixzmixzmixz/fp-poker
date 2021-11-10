package mixzpoker.game.poker

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.parser.decode
import mixzpoker.game.{GameError, GameId}
import mixzpoker.game.GameError._
import mixzpoker.game.poker.game.{PokerGame, PokerGameEvent}
import mixzpoker.infrastructure.broker.Broker
import PokerEvent._
import cats.ApplicativeError


trait PokerApp[F[_]] {
  def run: F[Unit]
  def processMessage(message: String): F[Unit]
  def getGame(gameId: GameId): EitherT[F, GameError, PokerGame]
}

object PokerApp {
  def of[F[_]: ConcurrentEffect](broker: Broker[F]): F[PokerApp[F]] = {
    for {
//      queue <- broker.getQueue("poker-game-topic").leftMap(GameBrokerError)
      pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])
    } yield new PokerApp[F] {

      override def run: F[Unit] = for {
        _ <- Sync[F].delay(println("run poker app!"))
        eithQ <- broker.getQueue("poker-game-topic").value
        _ <- eithQ match {
          case Left(err) => ().pure[F] // todo raiseError here
          case Right(queue) => queue.dequeue.evalMap(processMessage).compile.drain
        }
      } yield ()



      override def processMessage(message: String): F[Unit] = {
        for {
          event <- EitherT.fromEither[F](decode[PokerEvent](message).leftMap(err => DecodeError(err)))
          _ <- event match {
            case GameEvent(_, gameId, event) => processGameEvent(event, gameId)
            case e: CreateGameEvent => createGame(e)
          }
        } yield ()
      }.leftMap {_ => ()}.merge // todo log errs

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