package mixzpoker.lobby

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.Stream

import scala.concurrent.duration._
import mixzpoker.messages.lobby.{LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.messages.lobby.LobbyOutputMessage._
import mixzpoker.user.User

//todo make topic/queue per user?
trait LobbyService[F[_]] {
  def topic: Topic[F, LobbyOutputMessage]
  def queue: Queue[F, (LobbyInputMessage, User)]

  def run: F[Unit]
}

object LobbyService {
  def of[F[_]: Concurrent: Timer](repository: LobbyRepository[F]): F[LobbyService[F]] = for {
    _queue <- Queue.unbounded[F, (LobbyInputMessage, User)]
    _topic <- Topic[F, LobbyOutputMessage](Initial)
  } yield new LobbyService[F] {
    override def topic: Topic[F, LobbyOutputMessage] = _topic
    override def queue: Queue[F, (LobbyInputMessage, User)] = _queue

    override def run: F[Unit] = {
      val keepAlive = Stream
        .awakeEvery[F](30.seconds)
        .map(_ => KeepAlive)
        .through(topic.publish)

      val processingStream: Stream[F, Unit] = queue
        .dequeue
        .evalMap { case (message, user) => process(message, user) }
        .through(topic.publish)

      Stream(keepAlive, processingStream).parJoinUnbounded.compile.drain
    }

    def process(message: LobbyInputMessage, user: User): F[LobbyOutputMessage] = ???


  }
}
