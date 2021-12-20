package mixzpoker

import cats.{Functor, Parallel}
import cats.arrow.FunctionK
import cats.implicits._
import cats.data.{NonEmptySet => Nes}
import cats.effect.concurrent.Deferred
import cats.effect.syntax.all._
import cats.effect.{Clock, Concurrent, ConcurrentEffect, ContextShift, ExitCode, IO, Resource, Timer}
import com.evolutiongaming.catshelper.{Blocking, FromFuture, FromTry, ToFuture, ToTry}
import com.evolutiongaming.catshelper.CatsHelper._
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.skafka.{CommonConfig, Topic, TopicPartition}
import com.evolutiongaming.skafka.consumer._
import com.evolutiongaming.skafka.producer._
import com.evolutiongaming.smetrics.{CollectorRegistry, MeasureDuration}
import tofu.logging.Logging
import tofu.syntax.logging._

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._
import mixzpoker.domain.game.Topics


object GameServer {
  def run[F[_]: ConcurrentEffect: Timer: ContextShift: ToTry: ToFuture: FromTry: MeasureDuration]: F[ExitCode] = {

    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("MainLog")

    val topic = Topics.PokerTexasHoldemCommands

    val config = ConsumerConfig.Default.copy(
      groupId = Some(s"group-$topic"),
      autoOffsetReset = AutoOffsetReset.Earliest,
      autoCommit = false,
      common = CommonConfig(clientId = Some(UUID.randomUUID().toString))
    )

    implicit val executor: ExecutionContextExecutor = ExecutionContext.global

    implicit val contextShiftIO: ContextShift[IO]     = IO.contextShift(executor)
    implicit val concurrentIO: Concurrent[IO]         = IO.ioConcurrentEffect
    implicit val timerIO: Timer[IO]                   = IO.timer(executor)
    implicit val parallelIO: Parallel[IO]             = IO.ioParallel
    implicit val fromFutureIO: FromFuture[IO]         = FromFuture.lift[IO]
    implicit val measureDuration: MeasureDuration[IO] = MeasureDuration.fromClock[IO](Clock[IO])
    implicit val blocking: Blocking[IO]               = Blocking.empty[IO]

    val ecBlocking = executor

    def consumerOf(
      topic: Topic,
      listener: Option[RebalanceListener[F]]
    ): Resource[F, Consumer[F, String, String]] = {

      val config = ConsumerConfig.Default.copy(
        groupId = Some(s"group-$topic"),
        autoOffsetReset = AutoOffsetReset.Earliest,
        autoCommit = false,
        common = CommonConfig(clientId = Some(UUID.randomUUID().toString))
      )
      val consumerOf  = ConsumerOf[F](executor, None).mapK(FunctionK.id, FunctionK.id)

      for {
//        metrics    <- ConsumerMetrics.of(CollectorRegistry.empty[F])
//        consumerOf  = ConsumerOf[F](executor, metrics("clientId").some).mapK(FunctionK.id, FunctionK.id)

        consumer   <- consumerOf[String, String](config)
        _          <- consumer.subscribe(Nes.of(topic), listener).toResource
      } yield consumer
    }


    def listenerOf(assigned: Deferred[F, Unit]): RebalanceListener[F] = {
      new RebalanceListener[F] {

        def onPartitionsAssigned(partitions: Nes[TopicPartition]) = assigned.complete(())

        def onPartitionsRevoked(partitions: Nes[TopicPartition]) = ().pure[F]

        def onPartitionsLost(partitions: Nes[TopicPartition]) = ().pure[F]
      }
    }

//    val records: F[ConsumerRecords[String, String]] = consumer.use { consumer =>
//      for {
//        _       <- consumer.subscribe(Nel("topic"), None)
//        records <- consumer.poll(100.millis)
//      } yield records
//    }


//    val result = for {
//      assigned <- Deferred[F, Unit]
//      listener  = listenerOf(assigned = assigned)
//      consumer  = consumerOf(topic, listener.some)
//      res      <- consumer.use { consumer =>
//        for {
//          _       <- consumer.subscribe(Nel("topic"), None)
//          records <- consumer.poll(100.millis)
//        } yield records
////        val poll = consumer
////          .poll(10.millis)
////          .foreverM[Unit]
////        Resource
////          .make(poll.start) { _.cancel }
////          .use { _ => assigned.get.timeout(1.minute) }
//      }
//    } yield consumer

    for {
      assigned <- Deferred[F, Unit]
      listener  = listenerOf(assigned = assigned)
      consumer  = consumerOf(topic, listener.some)
      res      <- consumer.use { consumer =>
        for {
          _ <- consumer.subscribe(Nes.of("topic"))
          records <- consumer.poll(100.millis)
        } yield records
      }
    } yield ExitCode.Success

  }

}
