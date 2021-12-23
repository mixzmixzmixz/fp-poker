package mixzpoker.game.poker

import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, Resource, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._
import io.circe.parser.decode
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import tofu.generate.{GenRandom, GenUUID}
import tofu.logging.Logging
import tofu.syntax.logging._
import org.http4s.websocket.WebSocketFrame.Text
import mixzpoker.game.GameRecord
import mixzpoker.chat.ChatService
import mixzpoker.domain.game.GameError._
import mixzpoker.domain.game.poker.{PokerEvent, PokerEventContext, PokerGame, PokerGameState, PokerInputMessage, PokerSettings}
import mixzpoker.domain.game.{GameEventId, GameId}
import mixzpoker.domain.lobby.Lobby
import mixzpoker.domain.user.User
import mixzpoker.game.poker.PokerCommand._


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
  def of[F[_]: ConcurrentEffect: Logging: Timer: GenRandom]: F[PokerService[F]] = for {
    pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])

    //this is different and should be placed somewhere in reliable store in order to restore pokerManager if it fails
    gameRecords   <- Ref.of(Map.empty[GameId, GameRecord])
    commandQueue  <- Queue.unbounded[F, PokerCommandContext]
    chatService   <- ChatService.of[F, GameId]
  } yield new PokerService[F] {

    override def runBackground: Resource[F, F[Unit]] =
      commandQueue
        .dequeue
        .evalTap(e => info"Got event: ${e.toString}")
        .evalMap(handleCommand)
        .evalTap(_ => info"Successfully proceed an event")
        .compile
        .drain
        .background

    override def getGame(gameId: GameId): F[Option[PokerGame]] =
      pokerManagers
        .get
        .map(_.get(gameId))
        .flatMap(_.traverse(_.getGame))

    override def createGame(lobby: Lobby): F[GameId] = for {
      gameId <- GenUUID[F].randomUUID.map(GameId.fromUUID)
      gm     <- lobby.gameSettings match {
                  case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players)
                  case _                 => ConcurrentEffect[F].raiseError(WrongSettingsType)
                }
      _      <- pokerManagers.update { _.updated(gameId, gm) }
      _      <- gameRecords.update { _.updated(gameId, GameRecord(gameId, lobby.name)) }
      _      <- chatService.create(gameId)
      _      <- info"Created Poker Game(id=${gameId.toString})!"
    } yield gameId

    override def exists(gameId: GameId): F[Boolean] =
      gameRecords.get.map(_.contains(gameId))

    override def chatPipes(gameId: GameId): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]] =
      chatService.pipes(gameId)

    override def fromClientPipe(gameId: GameId): Pipe[F, (Option[User], String), Unit] =
      _.collect {
        case (Some(user), text) => (user, decode[PokerInputMessage](text).leftMap(_.toString))
      }.evalTap {
        case (user, Left(err))  => error"$err"
        case (user, Right(msg)) => info"Message: GameId=${gameId.toString}, message=${msg.toString}"
      }.collect {
        case (user, Right(msg)) => (user, msg)
      }.map {
        case (user, PokerInputMessage.Ping)          => PingCommand(user.id)
        case (user, PokerInputMessage.Join(buyIn))   => JoinCommand(user.id, buyIn, user.name)
        case (user, PokerInputMessage.Leave)         => LeaveCommand(user.id)
        case (user, PokerInputMessage.Fold)          => FoldCommand(user.id)
        case (user, PokerInputMessage.Check)         => CheckCommand(user.id)
        case (user, PokerInputMessage.Call)          => CallCommand(user.id)
        case (user, PokerInputMessage.Raise(amount)) => RaiseCommand(user.id, amount)
        case (user, PokerInputMessage.AllIn)         => AllInCommand(user.id)
      }.map { command =>
        PokerCommandContext(gameId, command)
      }.through(commandQueue.enqueue)

    override def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): F[Option[Stream[F, Text]]] =
      pokerManagers
        .get.map(_.get(gameId).map(_.toClient(gameId, userRef)))

    def handleCommand(commandContext: PokerCommandContext): F[Unit] =
      pokerManagers.get.flatMap(_.get(commandContext.gameId).traverse(_.handleCommand(commandContext.command))).map(_.getOrElse(()))
  }
}