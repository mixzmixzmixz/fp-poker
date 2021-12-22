package mixzpoker.chat

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{Pipe, Stream}
import fs2.concurrent.Topic
import io.circe.syntax._
import io.circe.parser.decode
import tofu.logging.Logging
import tofu.syntax.logging._
import org.http4s.websocket.WebSocketFrame.Text
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.chat.ChatInputMessage
import mixzpoker.domain.user.User

trait ChatService[F[_], K] {
  def pipes(chatId: K): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]]
  def create(chatId: K): F[Unit]
}

object ChatService {

  def of[F[_]: Concurrent: Logging, K]: F[ChatService[F, K]] = for {
    store <- Ref.of[F, Map[K, Topic[F, ChatOutputMessage]]](Map.empty)
  } yield
    new ChatService[F, K] {
      override def create(chatId: K): F[Unit] =
        Topic[F, ChatOutputMessage](ChatOutputMessage.KeepAlive)
          .flatMap { topic =>
            store.update(_.updated(chatId, topic))
          }

      def toClient(topic: Topic[F, ChatOutputMessage]): Stream[F, Text] =
        topic
          .subscribe(1000)
          .map(msg => Text(msg.asJson.noSpaces))

      def fromClient(
        chatId: K,
        topic: Topic[F, ChatOutputMessage]
      ): Pipe[F, (Option[User], String), Unit] =
        _.collect {
          case (Some(user), text) => (user, decode[ChatInputMessage](text).leftMap(_.toString))
        }.evalTap {
          case (user, Left(err))  => error"$err"
          case (user, Right(msg)) => info"ChatMsg: ChatId=${chatId.toString}, message=${msg.toString}"
        }.collect {
          case (user, Right(ChatInputMessage.ChatMessage(msg))) => ChatOutputMessage.ChatMessageFrom(msg, user)
        }.through(topic.publish)

      override def pipes(chatId: K): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]] =
        store.get.map(_.get(chatId)).map(_.map { topic =>
          (toClient(topic), fromClient(chatId, topic))
        })

    }
}
