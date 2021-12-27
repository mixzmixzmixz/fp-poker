package mixzpoker.game.poker

import cats.arrow.FunctionK
import cats.data.{NonEmptySet => Nes, NonEmptyList => Nel}
import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.evolutiongaming.catshelper.{FromTry, Log, ToFuture, ToTry}
import com.evolutiongaming.catshelper.CatsHelper._
import com.evolutiongaming.skafka.{CommonConfig, Topic}
import com.evolutiongaming.skafka.consumer.{AutoOffsetReset, Consumer, ConsumerConfig, ConsumerOf, RebalanceListener}
import com.evolutiongaming.skafka.producer.{Acks, Producer, ProducerConfig, ProducerOf, ProducerRecord}
import com.evolutiongaming.smetrics.MeasureDuration
import io.circe.parser.decode
import io.circe.syntax._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import mixzpoker.Config
import tofu.generate.{GenRandom, GenUUID}
import tofu.logging.Logging
import tofu.syntax.logging._
import org.http4s.websocket.WebSocketFrame.Text
import mixzpoker.game.GameRecord
import mixzpoker.chat.ChatService
import mixzpoker.domain.game.GameError._
import mixzpoker.domain.game.poker.{PokerEvent, PokerGame, PokerInputMessage, PokerSettings}
import mixzpoker.domain.game.{GameId, Topics}
import mixzpoker.domain.lobby.Lobby
import mixzpoker.domain.user.User
import mixzpoker.game.poker.PokerCommand._
import mixzpoker.infrastructure.broker.KafkaProducer

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._


trait PokerService[F[_]] {
  private[poker] def runBackground: Resource[F, F[Unit]]
  def getGame(gameId: GameId): F[Option[PokerGame]]
  def createGame(lobby: Lobby): F[GameId]
  def exists(gameId: GameId): F[Boolean]
  def storeEvent(event: PokerEvent, gameId: GameId): F[Unit]
  def saveSnapshot(game: PokerGame, gameId: GameId): F[Unit]

  def chatPipes(gameId: GameId): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]]
  def fromClientPipe(gameId: GameId): Pipe[F, (Option[User], String), Unit]
  def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): F[Option[Stream[F, Text]]]
}

object PokerService {
  def of[
    F[_]: ConcurrentEffect: Logging: Timer: GenUUID: GenRandom: ContextShift: ToTry: ToFuture: FromTry: MeasureDuration
  ]: F[Resource[F, PokerService[F]]] = {

    implicit val executor: ExecutionContextExecutor = ExecutionContext.global
    val topic = Topics.pokerTexasHoldemCommands

    //todo tailrec? do we need to care about it in ce?
    def consume(consumer: Consumer[F, String, String], queue: Queue[F, PokerCommandContext]): F[Unit] = {
      for {
        records <- consumer.poll(1.second)
        _ <- if (records.values.nonEmpty) info"got messages: ${records.values.toList.toString}" else ().pure[F]
        _ <- records.values.values.toList.traverse(_.traverse { record =>
          (record.key, record.value)
            .mapN { case (k, v) =>
              (GameId.fromString(k.value).toOption, decode[PokerCommand](v.value).toOption)
            }.flatMap(_.mapN { PokerCommandContext })
            .fold(info"Error on parsing PokerCommand") { queue.enqueue1 }
          })
        _ <- consume(consumer, queue)
      } yield ()
    }

    def consumerOf(
      topic: Topic,
      listener: Option[RebalanceListener[F]],
      consumerUUUID: UUID
    ): Resource[F, Consumer[F, String, String]] = {

      val config = ConsumerConfig.Default.copy(
        groupId = Some(s"group-$topic"),
        autoOffsetReset = AutoOffsetReset.Latest,
        autoCommit = false,
        common = CommonConfig(
          clientId = consumerUUUID.toString.some,
          bootstrapServers = Nel.of(s"${Config.KAFKA_HOST}:9092")
        )
      )
      val consumerOf  = ConsumerOf[F](executor, None).mapK(FunctionK.id, FunctionK.id)

      for {
        consumer   <- consumerOf[String, String](config)
        _          <- consumer.subscribe(Nes.of(topic), listener).toResource
      } yield consumer
    }

    def producerOf(acks: Acks): Resource[F, Producer[F]] = {
      val config = ProducerConfig.Default.copy(
        acks = acks,
        common = CommonConfig.Default.copy(
          bootstrapServers = Nel.of(s"${Config.KAFKA_HOST}:9092")
        )
      )
      val producerOf = ProducerOf.apply(executor, None).mapK(FunctionK.id, FunctionK.id)
      producerOf(config).map(_.withLogging(Log.empty))  // todo do I need logs here? replace with tofu logs somehow?
    }


    for {
      kpEventsRes   <- KafkaProducer.of[F, PokerEvent, GameId](Topics.pokerTexasHoldemEvents)
      kpSnapshotsRes<- KafkaProducer.of[F, PokerGame, GameId](Topics.Compact.pokerTexasHoldemSnapshots)
      pokerManagers <- Ref.of(Map.empty[GameId, PokerGameManager[F]])

      //this is different and should be placed somewhere in reliable store in order to restore pokerManager if it fails
      gameRecords   <- Ref.of(Map.empty[GameId, GameRecord])
      commandQueue  <- Queue.unbounded[F, PokerCommandContext]
      chatService   <- ChatService.of[F, GameId]
      consumerUUID  <- GenUUID[F].randomUUID
    } yield {
      for {
        kpEvents      <- kpEventsRes
        kpSnapshots   <- kpSnapshotsRes
        consumerCmds  <- consumerOf(topic, None, Config.KAFKA_CONSUMER_UUID)
        producerCmds  <- producerOf(Acks.One)
        _             <- consume(consumerCmds, commandQueue).background
        pokerService  =  new PokerService[F] {

          override private[poker] def runBackground: Resource[F, F[Unit]] =
            commandQueue
              .dequeue
              .evalTap(cmdCtx => info"GameId=${cmdCtx.gameId.toString} Command: ${cmdCtx.command.toString}")
              .evalMap(handleCommand)
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
              case ps: PokerSettings => PokerGameManager.create(gameId, ps, lobby.players, storeEvent, saveSnapshot)
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
            }
              //.through(commandQueue.enqueue)  // No Kafka Mode
              .through(kafkaCommandTopicPipe) //Kafka Mode

          override def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): F[Option[Stream[F, Text]]] =
            pokerManagers
              .get.map(_.get(gameId).map(_.toClient(gameId, userRef)))

          def handleCommand(commandContext: PokerCommandContext): F[Unit] =
            pokerManagers.get.flatMap(_.get(commandContext.gameId).traverse(_.handleCommand(commandContext.command))).map(_.getOrElse(()))

          override def storeEvent(event: PokerEvent, gameId: GameId): F[Unit] =
            kpEvents.publishEvent(event, gameId)

          def kafkaCommandTopicPipe: Pipe[F, PokerCommandContext, Unit] =
            _.evalTap { cmdCtx =>
              producerCmds.send(ProducerRecord(topic, cmdCtx.command.asJson.noSpaces, cmdCtx.gameId.toString)).flatten
            }.evalTap { cmdCtx =>
              info"Command sent to kafka: GameId=${cmdCtx.gameId.toString}, Command=${cmdCtx.command.toString}"
            }.map(_ => ())

          override def saveSnapshot(game: PokerGame, gameId: GameId): F[Unit] =
            kpSnapshots.publishEvent(game, gameId)
        }
        _             <- pokerService.runBackground
      } yield pokerService
    }
  }
}