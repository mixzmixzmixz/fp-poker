package mixzpoker.game.poker

import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, Resource, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.syntax._
import io.circe.parser.decode
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._
import org.http4s.websocket.WebSocketFrame.Text
import mixzpoker.game.GameRecord
import mixzpoker.chat.ChatService
import mixzpoker.domain.game.GameError._
import mixzpoker.domain.game.poker.PokerEvent.PokerPlayerEvent
import mixzpoker.domain.game.poker.{PokerEvent, PokerEventContext, PokerGame, PokerGameState, PokerOutputMessage, PokerSettings}
import mixzpoker.domain.game.{GameError, GameEventId, GameId}
import mixzpoker.domain.lobby.Lobby
import mixzpoker.domain.user.User


trait PokerService[F[_]] {
  def runBackground: Resource[F, F[Unit]]
  def getGame(gameId: GameId): F[Option[PokerGame]]
  def createGame(lobby: Lobby): F[GameId]
  def exists(gameId: GameId): F[Boolean]

  def chatPipes(gameId: GameId): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]]
  def fromClientPipe(gameId: GameId): Pipe[F, (Option[User], String), Unit]
  def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): F[Option[Stream[F, Text]]]
}

object PokerService {
  //todo of -> resource
  def of[F[_]: ConcurrentEffect: Logging: Timer]: F[PokerService[F]] = for {
    pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])

    //this is different and should be placed somewhere in reliable store in order to restore pokerManager if it fails
    gameRecords   <- Ref.of(Map.empty[GameId, GameRecord])
    queue         <- Queue.unbounded[F, PokerEventContext]
    chatService   <- ChatService.of[F, GameId]
  } yield new PokerService[F] {

    override def runBackground: Resource[F, F[Unit]] =
      //info"Run poker App!" *>
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

    override def getGame(gameId: GameId): F[Option[PokerGame]] =
      pokerManagers
        .get
        .map(_.get(gameId))
        .flatMap(_.traverse(_.getGame))


    override def createGame(lobby: Lobby): F[GameId] = for {
      gameId <- GenUUID[F].randomUUID.map(GameId.fromUUID)
      gm     <- lobby.gameSettings match {
                  case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players, queue)
                  case _                 => ConcurrentEffect[F].raiseError(WrongSettingsType)
                }
      _      <- pokerManagers.update { _.updated(gameId, gm) }
      _      <- gameRecords.update { _.updated(gameId, GameRecord(gameId, lobby.name)) }
      eid    <- GenUUID[F].randomUUID.map(GameEventId.fromUUID)
      _      <- queue.enqueue1(PokerEventContext(eid, gameId, None, PokerEvent.NextState(PokerGameState.RoundStart)))
      _      <- info"Created Poker Game(id=${gameId.toString})!"
    } yield gameId

    override def exists(gameId: GameId): F[Boolean] =
      gameRecords.get.map(_.contains(gameId))

    override def chatPipes(gameId: GameId): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]] =
      chatService.pipes(gameId)

    override def fromClientPipe(gameId: GameId): Pipe[F, (Option[User], String), Unit] =
      _.collect {
        case (Some(user), text) => (user, decode[PokerPlayerEvent](text).leftMap(_.toString))
      }.evalTap {
        case (user, Left(err))  => error"$err"
        case (user, Right(msg)) => info"Event: GameId=${gameId.toString}, message=${msg.toString}"
      }.collect {
        case (user, Right(event)) => (user, event)
      }.evalMap { case (user, event) =>
        GenUUID[F].randomUUID.map(uuid =>
          PokerEventContext(id = GameEventId.fromUUID(uuid), gameId, user.id.some, event)
        )
      }.through(queue.enqueue)

    override def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): F[Option[Stream[F, Text]]] =
      pokerManagers
        .get.map(_.get(gameId).map(_.topic))
        .map {
          case Some(topic) =>
            topic
              .subscribe(1000)
              .evalFilter {
                case PokerOutputMessage.ErrorMessage(Some(id), _) => userRef.get.map(_.fold(false)(_.id == id))
                case _  => true.pure[F]
              }.map(msg => Text(msg.asJson.noSpaces))
              .some
          case None => none[Stream[F, Text]]
        }


  }
}