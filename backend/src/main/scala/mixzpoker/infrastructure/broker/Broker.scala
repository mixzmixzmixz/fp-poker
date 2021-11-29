package mixzpoker.infrastructure.broker

import cats.implicits._
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import fs2.concurrent.Queue

import BrokerError._


trait Broker[F[_]] {
  def getMessage(id: String): F[String]
  def sendMessage(id: String, message: String): F[Unit]
  def createTopic(id: String): F[Unit]
  def deleteTopic(id: String): F[Unit]

  def getQueue(id: String): F[Queue[F, String]]
}

object Broker {

  def fromQueues[F[_]: Concurrent](bound: Int): F[Broker[F]] = for {
    store <- Ref.of(Map.empty[String, Queue[F, String]])

  } yield new Broker[F] {
    override def getMessage(id: String): F[String] = for {
      map <- store.get
      q <- map.get(id).toRight[BrokerError](NoSuchTopic).liftTo[F]
      msg <- q.dequeue1
    } yield msg

    override def sendMessage(id: String, message: String): F[Unit] = for {
      map <- store.get
      q <- map.get(id).toRight[BrokerError](NoSuchTopic).liftTo[F]
      _ <- q.enqueue1(message)
    } yield()

    override def createTopic(id: String): F[Unit] = for {
        exist <- store.get.map(_.contains(id))
        _ <- Either.cond(!exist, (), TopicAlreadyExist).liftTo[F]
        q <- Queue.bounded[F, String](bound)
        _ <- store.update(_ + (id -> q))
      } yield ()

    override def deleteTopic(id: String): F[Unit] = for {
        exist <- store.get.map(_.contains(id))
        _ <- Either.cond(exist, (), NoSuchTopic).liftTo[F]
        _ <- store.update(_ - id)
      } yield ()

    override def getQueue(id: String): F[Queue[F, String]] = for {
      map <- store.get
      q <- map.get(id).toRight[BrokerError](NoSuchTopic).liftTo[F]
    } yield q
  }
}
