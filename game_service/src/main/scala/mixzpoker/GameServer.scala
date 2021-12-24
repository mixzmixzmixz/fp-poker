package mixzpoker

import cats.arrow.FunctionK
import cats.implicits._
import cats.data.{NonEmptySet => Nes}
import cats.effect.concurrent.Deferred
import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, Resource, Timer}
import com.evolutiongaming.catshelper.{FromTry, Log, ToFuture, ToTry}
import com.evolutiongaming.catshelper.CatsHelper._
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
    implicit val executor: ExecutionContextExecutor = ExecutionContext.global

    val topic = Topics.PokerTexasHoldemCommands

    val config = ConsumerConfig.Default.copy(
      groupId = Some(s"group-$topic"),
      autoOffsetReset = AutoOffsetReset.Earliest,
      autoCommit = false,
      common = CommonConfig(clientId = Some(UUID.randomUUID().toString))
    )

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

    def producerOf(acks: Acks): Resource[F, Producer[F]] = {
      val config = ProducerConfig.Default.copy(acks = acks)
      val producerOf = ProducerOf.apply(executor, None).mapK(FunctionK.id, FunctionK.id)
      for {
//        metrics    <- ProducerMetrics.of(CollectorRegistry.empty[F])

        producer   <- producerOf(config)
      } yield {
        producer.withLogging(Log.empty)
      }
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
      producer  = producerOf(Acks.One)

      _ <- producer.use { producer =>
        for {
          _ <- info"Send my value1"
          _ <- producer.send(ProducerRecord(topic, "hello", "there")).flatten
          _ <- info"Send my value2"
          _ <- producer.send(ProducerRecord(topic, "obi-wan", "kenobi")).flatten
        } yield ()
      }

      _ <- info"poll my values"
      res <- consumer.use { consumer =>
        for {
          _ <- consumer.subscribe(Nes.of("topic"))
          records <- consumer.poll(100.millis)
        } yield records
      }
      _ <- info"polled: ${res.toString}"

    } yield ExitCode.Success

  }

}
