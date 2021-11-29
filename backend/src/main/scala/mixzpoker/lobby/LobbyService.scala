package mixzpoker.lobby

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.Stream
import mixzpoker.domain.Token
import mixzpoker.domain.lobby.LobbyOutputMessage
import tofu.logging.Logging
import tofu.syntax.logging._

import scala.concurrent.duration._
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.user.User

//todo make topic/queue per user?
trait LobbyService[F[_]] {
  def topic: Topic[F, LobbyOutputMessage]
  def queue: Queue[F, LobbyEvent]

  def run: F[Unit]
}

object LobbyService {
  def of[F[_]: Concurrent: Timer: Logging](repository: LobbyRepository[F]): F[LobbyService[F]] = for {
    _queue <- Queue.unbounded[F, LobbyEvent]
    _topic <- Topic[F, LobbyOutputMessage](KeepAlive)
  } yield new LobbyService[F] {
    override def topic: Topic[F, LobbyOutputMessage] = _topic
    override def queue: Queue[F, LobbyEvent] = _queue

    override def run: F[Unit] = {
      val keepAlive = Stream.awakeEvery[F](30.seconds).map(_ => KeepAlive).through(topic.publish)
      val processingStream = queue.dequeue.evalMap(process).through(topic.publish)

      info"LobbyService started!" *> Stream(keepAlive, processingStream).parJoinUnbounded.compile.drain
    }

    def process(event: LobbyEvent): F[LobbyOutputMessage] = event.message match {
      case Join(buyIn)          => joinLobby(event.lobbyName, event.user, buyIn)
      case Leave                => leaveLobby(event.lobbyName, event.user)
      case ChatMessage(message) => chat(message, event.user)
    }

    def joinLobby(lobbyName: LobbyName, user: User, buyIn: Token): F[LobbyOutputMessage] = for {
      lobby  <- repository.get(lobbyName)
      lobby2 <- lobby.joinPlayer(user, buyIn).liftTo[F]
      _      <- repository.save(lobby2)
    } yield LobbyState(lobby = lobby2.dto)

    def leaveLobby(lobbyName: LobbyName, user: User): F[LobbyOutputMessage] = for {
      lobby  <- repository.get(lobbyName)
      lobby2 <- lobby.leavePlayer(user).liftTo[F]
      _      <- repository.save(lobby2)
    } yield LobbyState(lobby = lobby2.dto)

    def chat(message: String, user: User): F[LobbyOutputMessage] =
      (ChatMessageFrom(message, user.dto): LobbyOutputMessage).pure[F]
  }
}
