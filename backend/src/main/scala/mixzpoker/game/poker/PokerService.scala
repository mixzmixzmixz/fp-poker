package mixzpoker.game.poker

import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import tofu.logging.Logging
import tofu.syntax.logging._
import java.util.UUID

import mixzpoker.game.{GameError, GameRecord}
import mixzpoker.game.GameError._
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.game.poker.{PokerEventContext, PokerGame, PokerOutputMessage, PokerSettings}
import mixzpoker.domain.game.GameId
import mixzpoker.lobby.Lobby


trait PokerService[F[_]] {
  def run: F[Unit]
  def getGame(gameId: GameId): F[PokerGame]
  def createGame(lobby: Lobby): F[GameId]
  def ensureExists(gameId: GameId): F[Unit]
  def getTopic(gameId: GameId): F[Topic[F, PokerOutputMessage]]
  def getChatTopic(gameId: GameId): F[Topic[F, ChatOutputMessage]]
  def queue: Queue[F, PokerEventContext]
}

object PokerService {
  def of[F[_]: ConcurrentEffect: Logging]: F[PokerService[F]] = for {
    pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])
    //this is different and should be placed somewhere in reliable store in order to restore pokerManager if it fails
    gameRecords   <- Ref.of(Map.empty[GameId, GameRecord])
    _queue        <- Queue.unbounded[F, PokerEventContext]
  } yield new PokerService[F] {
    override def queue: Queue[F, PokerEventContext] = _queue

    override def run: F[Unit] =
      info"Run poker App!" *>
        queue
          .dequeue
          .evalTap(e => info"Got event: ${e.toString}")
          .evalMap(processEvent)
          .evalTap(_ => info"Successfully proceed an event")
          .compile
          .drain

    def processEvent(e: PokerEventContext): F[Unit] = for {
      pm  <- pokerManagers.get.map(_.get(e.gameId).toRight[GameError](NoSuchGame)).flatMap(_.liftTo[F])
      res <- pm.processEvent(e)
      _   <- pm.topic.publish1(res)
    } yield ()

    override def getGame(gameId: GameId): F[PokerGame] = pokerManagers
      .get
      .map(_.get(gameId).toRight[GameError](NoSuchGame))
      .flatMap(_.liftTo[F])
      .flatMap(_.getGame)


    override def createGame(lobby: Lobby): F[GameId] = for {
      _      <- info"Create GameRecord!"
      uuid   <- { UUID.randomUUID() }.pure[F]
      gameId =  GameId.fromUUID(uuid)
      gm     <- lobby.gameSettings match {
        case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players, _queue)
        case _                 => ConcurrentEffect[F].raiseError(WrongSettingsType)
      }
      _      <- pokerManagers.update { _.updated(gameId, gm) }
      _      <- gameRecords.update { _.updated(gameId, GameRecord(gameId, lobby.name)) }
    } yield gameId

    override def ensureExists(gameId: GameId): F[Unit] =
      gameRecords.get.map(_.contains(gameId)).flatMap(b => Either.cond(b, (), NoSuchGame).liftTo[F])

    override def getTopic(gameId: GameId): F[Topic[F, PokerOutputMessage]] =
      pokerManagers.get.flatMap(_.get(gameId).map(_.topic).toRight[GameError](NoSuchGame).liftTo[F])

    override def getChatTopic(gameId: GameId): F[Topic[F, ChatOutputMessage]] =
      pokerManagers.get.flatMap(_.get(gameId).map(_.chatTopic).toRight[GameError](NoSuchGame).liftTo[F])

  }
}