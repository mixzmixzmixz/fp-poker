package mixzpoker



import cats.Parallel
import cats.arrow.FunctionK
import cats.implicits._
import cats.data.{NonEmptySet => Nes}
import cats.effect.{Clock, Concurrent, ConcurrentEffect, ContextShift, ExitCode, IO, Resource, Timer}
import com.evolutiongaming.catshelper.{Blocking, FromFuture, FromTry, ToFuture, ToTry}
import com.evolutiongaming.catshelper.CatsHelper._
import com.evolutiongaming.nel.Nel
import com.evolutiongaming.skafka.{CommonConfig, Topic}
import com.evolutiongaming.skafka.consumer._
import com.evolutiongaming.skafka.producer._
import com.evolutiongaming.smetrics.{CollectorRegistry, MeasureDuration}
import tofu.logging.Logging
import tofu.syntax.logging._

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

import mixzpoker.domain.game.Topics


object GameServer {
  def run[F[_]: ConcurrentEffect: Timer: ContextShift: ToTry: ToFuture: FromTry]: F[ExitCode] = {

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

//    def consumerOf2(
//      topic: Topic,
//      listener: Option[RebalanceListener[F]]
//    ): Resource[F, Consumer[F, String, String]] = {
//
//      val config = ConsumerConfig.Default.copy(
//        groupId = Some(s"group-$topic"),
//        autoOffsetReset = AutoOffsetReset.Earliest,
//        autoCommit = false,
//        common = CommonConfig(clientId = Some(UUID.randomUUID().toString))
//      )
//      val consumerOf  = ConsumerOf[F](executor, None).mapK(FunctionK.id, FunctionK.id)
//
//      for {
////        metrics    <- ConsumerMetrics.of(CollectorRegistry.empty[F])
////        consumerOf  = ConsumerOf[F](executor, metrics("clientId").some).mapK(FunctionK.id, FunctionK.id)
//
//        consumer   <- consumerOf[String, String](config)
//        _          <- consumer.subscribe(Nes.of(topic), listener).toResource
//      } yield consumer
//    }
//
//    val records: F[ConsumerRecords[String, String]] = consumer.use { consumer =>
//      for {
//        _       <- consumer.subscribe(Nel("topic"), None)
//        records <- consumer.poll(100.millis)
//      } yield records
//    }

    def randomUUID(): F[UUID] = { UUID.randomUUID() } .pure[F]

    def randomUUID2(): F[UUID] = UUID.randomUUID().pure[F]


    def getUUIDS(): F[Unit] = for {
      u1 <- randomUUID2()
      _ <- logging.info(s"${u1.toString}")

      u2 <- randomUUID2()
      _ <- logging.info(s"${u2.toString}")
    }  yield ()


    for {
      _ <- getUUIDS()
      _ <- ().pure[F]

    } yield ExitCode.Success

  }

}
