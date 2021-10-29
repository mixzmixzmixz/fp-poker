package mixzpoker.infrastructure.broker

import cats.implicits._
import BrokerError._
import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import fs2.concurrent.Queue


trait Broker[F[_], Message, TopicId] {
  def getMessage(id: TopicId): F[ErrOr[Message]]
  def sendMessage(id: TopicId, message: Message): F[ErrOr[Unit]]
  def createTopic(id: TopicId): F[ErrOr[Unit]]
  def deleteTopic(id: TopicId): F[ErrOr[Unit]]

  def getQueue(id: TopicId): F[ErrOr[Queue[F, Message]]]

}

object Broker {

  def fromQueues[F[_]: Concurrent, Message, TopicId](bound: Int): F[Broker[F, Message, TopicId]] = for {
    store <- Ref.of(Map.empty[TopicId, Queue[F, Message]])
  } yield new Broker[F, Message, TopicId] {
    override def getMessage(id: TopicId): F[ErrOr[Message]] = {
      for {
        q <- EitherT(store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic)))
        msg <- EitherT.right[BrokerError](q.dequeue1)
      } yield msg
    }.value

    override def sendMessage(id: TopicId, message: Message): F[ErrOr[Unit]] = {
      for {
        q <- EitherT(store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic)))
        _ <- EitherT.right[BrokerError](q.enqueue1(message))
      } yield()
    }.value

    override def createTopic(id: TopicId): F[ErrOr[Unit]] = {
      for {
        exist <- EitherT.right[BrokerError](store.get.map(_.contains(id)))
        _ <- EitherT.cond[F](!exist, (), TopicAlreadyExist)
        q <- EitherT.right[BrokerError](Queue.bounded[F, Message](bound))
        _ <- EitherT.right[BrokerError](store.update(_ + (id -> q)))
      } yield ()
    }.value

    override def deleteTopic(id: TopicId): F[ErrOr[Unit]] = {
      for {
        exist <- EitherT.right[BrokerError](store.get.map(_.contains(id)))
        _ <- EitherT.cond[F](exist, (), NoSuchTopic)
        _ <- EitherT.right[BrokerError](store.update(_ - id))
      } yield ()
    }.value

    override def getQueue(id: TopicId): F[ErrOr[Queue[F, Message]]] =
      store.get.map(_.get(id).toRight[BrokerError](NoSuchTopic))
  }
}
