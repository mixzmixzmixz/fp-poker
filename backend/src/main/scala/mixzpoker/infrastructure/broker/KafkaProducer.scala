package mixzpoker.infrastructure.broker

import cats.implicits._
import cats.effect.syntax.all._
import cats.arrow.FunctionK
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import tofu.logging.Logging
import tofu.syntax.logging._

import com.evolutiongaming.catshelper.{FromTry, Log, ToFuture, ToTry}
import com.evolutiongaming.skafka.producer._
import com.evolutiongaming.smetrics.MeasureDuration
import fs2.concurrent.Queue
import io.circe.syntax._
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


// tiny wrapper in order to encapsulate all the dependencies here
trait KafkaProducer[F[_], Event, Key] {
  def publishEvent(event: Event, key: Key): F[Unit]
}

object KafkaProducer {
  def of[
    F[_]: ConcurrentEffect: Timer: ContextShift: ToTry: ToFuture: FromTry: MeasureDuration: Logging,
    Event: Encoder,
    Key
  ](topic: String): F[Resource[F, KafkaProducer[F, Event, Key]]] = {
    implicit val executor: ExecutionContextExecutor = ExecutionContext.global

    def producerOf(acks: Acks): Resource[F, Producer[F]] = {
      val config = ProducerConfig.Default.copy(acks = acks)
      val producerOf = ProducerOf.apply(executor, None).mapK(FunctionK.id, FunctionK.id)
      producerOf(config).map(_.withLogging(Log.empty))  // todo do I need logs here? replace with tofu logs somehow?
    }

    def process(queue: Queue[F, (Event, Key)], producer: Producer[F]) = {
      queue
        .dequeue
        .evalMap { case (event, key) =>
          producer.send(ProducerRecord(topic, event.asJson.noSpaces, key.toString)).flatten
        }.evalTap { meta =>
          info"Meta ${meta.toString}"
        }.compile
          .drain
          .background
    }

    Queue.bounded[F, (Event, Key)](1024).map { queue =>
      for {
        producer <- producerOf(Acks.One)
        _ <- process(queue, producer)
      } yield new KafkaProducer[F, Event, Key] {
        override def publishEvent(event: Event, key: Key): F[Unit] =
          queue.enqueue1((event, key))
      }
    }
  }
}
