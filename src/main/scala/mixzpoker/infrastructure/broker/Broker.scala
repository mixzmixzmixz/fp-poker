package mixzpoker.infrastructure.broker

import cats.implicits._
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import fs2.concurrent.Queue

import BrokerError._


trait Broker[F[_]] {
  def getMessage(id: String): EitherT[F, BrokerError, String]
  def sendMessage(id: String, message: String): EitherT[F, BrokerError, Unit]
  def createTopic(id: String): EitherT[F, BrokerError, Unit]
  def deleteTopic(id: String): EitherT[F, BrokerError, Unit]

  def getQueue(id: String): EitherT[F, BrokerError, Queue[F, String]]

}

object Broker {

  def fromQueues[F[_]: Concurrent](bound: Int): F[Broker[F]] = for {
    store <- Ref.of(Map.empty[String, Queue[F, String]])

  } yield new Broker[F] {
    override def getMessage(id: String): EitherT[F, BrokerError, String] = for {
        q <- EitherT(store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic)))
        msg <- EitherT.right[BrokerError](q.dequeue1)
      } yield msg

    override def sendMessage(id: String, message: String): EitherT[F, BrokerError, Unit] = for {
        q <- EitherT(store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic)))
        _ <- EitherT.right[BrokerError](q.enqueue1(message))
      } yield()

    override def createTopic(id: String): EitherT[F, BrokerError, Unit] = for {
        exist <- EitherT.right[BrokerError](store.get.map(_.contains(id)))
        _ <- EitherT.cond[F](!exist, (), TopicAlreadyExist)
        q <- EitherT.right[BrokerError](Queue.bounded[F, String](bound))
        _ <- EitherT.right[BrokerError](store.update(_ + (id -> q)))
      } yield ()

    override def deleteTopic(id: String): EitherT[F, BrokerError, Unit] = for {
        exist <- EitherT.right[BrokerError](store.get.map(_.contains(id)))
        _ <- EitherT.cond[F](exist, (), NoSuchTopic)
        _ <- EitherT.right[BrokerError](store.update(_ - id))
      } yield ()

    override def getQueue(id: String): EitherT[F, BrokerError, Queue[F, String]] =
      EitherT(store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic)))
  }
}
