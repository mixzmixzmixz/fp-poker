package mixzpoker.game.poker

import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, Timer, Resource}
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.game.GameRecord
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.game.GameError._
import mixzpoker.domain.game.poker.{PokerEvent, PokerEventContext, PokerGame, PokerGameState, PokerOutputMessage, PokerSettings}
import mixzpoker.domain.game.{GameError, GameEventId, GameId}
import mixzpoker.domain.lobby.Lobby


trait PokerService[F[_]] {
  def runBackground: Resource[F, F[Unit]]
  def getGame(gameId: GameId): F[PokerGame]
  def createGame(lobby: Lobby): F[GameId]
  def ensureExists(gameId: GameId): F[Unit]
  def getTopic(gameId: GameId): F[Topic[F, PokerOutputMessage]]
  def getChatTopic(gameId: GameId): F[Topic[F, ChatOutputMessage]]
  def queue: Queue[F, PokerEventContext]
}

object PokerService {
  //todo of -> resource
  def of[F[_]: ConcurrentEffect: Logging: Timer]: F[PokerService[F]] = for {
    pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])
    //this is different and should be placed somewhere in reliable store in order to restore pokerManager if it fails
    gameRecords   <- Ref.of(Map.empty[GameId, GameRecord])
    _queue        <- Queue.unbounded[F, PokerEventContext]
  } yield new PokerService[F] {
    override def queue: Queue[F, PokerEventContext] = _queue

    override def runBackground: Resource[F, F[Unit]] =
//      info"Run poker App!" *>
        queue
          .dequeue
          .evalTap(e => info"Got event: ${e.toString}")
          .evalMap(processEvent)
          .evalTap(_ => info"Successfully proceed an event")
          .compile
          .drain
          .background

    def processEvent(e: PokerEventContext): F[Unit] = for {
      pm  <- pokerManagers.get.flatMap(_.get(e.gameId).toRight[GameError](NoSuchGame).liftTo[F])
      res <- pm.processEvent(e)
      _   <- pm.topic.publish1(res)
    } yield ()

    override def getGame(gameId: GameId): F[PokerGame] = pokerManagers
      .get
      .map(_.get(gameId).toRight[GameError](NoSuchGame))
      .flatMap(_.liftTo[F])
      .flatMap(_.getGame)

    override def createGame(lobby: Lobby): F[GameId] = for {
      gameId <- GenUUID[F].randomUUID.map(GameId.fromUUID)
      gm     <- lobby.gameSettings match {
                  case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players, _queue)
                  case _                 => ConcurrentEffect[F].raiseError(WrongSettingsType)
                }
      _      <- pokerManagers.update { _.updated(gameId, gm) }
      _      <- gameRecords.update { _.updated(gameId, GameRecord(gameId, lobby.name)) }
      eid    <- GenUUID[F].randomUUID.map(GameEventId.fromUUID)
      _      <- queue.enqueue1(PokerEventContext(eid, gameId, None, PokerEvent.NextState(PokerGameState.RoundStart)))
      _      <- info"Created Poker Game(id=${gameId.toString})!"
    } yield gameId

    override def ensureExists(gameId: GameId): F[Unit] =
      gameRecords.get.map(_.contains(gameId)).flatMap(b => Either.cond(b, (), NoSuchGame).liftTo[F])

    override def getTopic(gameId: GameId): F[Topic[F, PokerOutputMessage]] =
      pokerManagers.get.flatMap(_.get(gameId).map(_.topic).toRight[GameError](NoSuchGame).liftTo[F])

    override def getChatTopic(gameId: GameId): F[Topic[F, ChatOutputMessage]] =
      pokerManagers.get.flatMap(_.get(gameId).map(_.chatTopic).toRight[GameError](NoSuchGame).liftTo[F])

  }
}