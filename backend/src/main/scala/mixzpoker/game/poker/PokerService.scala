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
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.lobby.Lobby


trait PokerService[F[_]] {
  def run: F[Unit]
  def processMessage(message: String): F[Unit]
  def getGame(gameId: GameId): F[PokerGame]
  def createGame(lobby: Lobby): F[GameId]
}

object PokerService {
  def of[F[_]: ConcurrentEffect: Logging](broker: Broker[F]): F[PokerService[F]] = for {
    pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])
  } yield new PokerService[F] {

    override def run: F[Unit] = for {
      _ <- info"Run poker App!"
      q <- broker.getQueue("poker-game-topic")
      _ <- q.dequeue
        .evalTap(msg => info"Got msg: $msg")
        .map(msg => decode[PokerEvent](msg).leftMap(err => DecodeError(err)))
        .evalTap {
          case Left(err) => error"${err.toString}"
          case Right(event) => info"Got event: ${event.toString}"
        }
        .collect { case Right(e) => e }
        .evalMap(processEvent)
        .evalTap(_ => info"Successfully proceed message")
        .compile
        .drain
    } yield ()

    override def processEvent(e: PokerEvent): F[Either[GameError, Unit]] = {
      e match {
        case GameEvent(_, gameId, event) => processGameEvent(event, gameId)
      }
    }

    override def getGame(gameId: GameId): F[PokerGame] = pokerManagers
      .get
      .map(_.get(gameId).toRight[GameError](NoSuchGame))
      .flatMap(_.liftTo[F])
      .flatMap(_.game)


    private def processGameEvent(event: PokerGameEvent, gameId: GameId): F[Unit] = pokerManagers
      .get
      .map(_.get(gameId).toRight[GameError](NoSuchGame))
      .flatMap(_.liftTo[F])
      .flatMap(_.processEvent(event))


    def createGame(lobby: Lobby): F[GameId] = for {
      _      <- info"Create Game!"
      gameId <- GameId.fromRandom
      gm     <- lobby.gameSettings match {
        case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players, broker).map(_.liftTo[F])
        case _                 => ConcurrentEffect[F].raiseError(WrongSettingsType)
      }
      _      <- pokerManagers.update { _.updated(gameId, gm) }
    } yield gameId
  }
}