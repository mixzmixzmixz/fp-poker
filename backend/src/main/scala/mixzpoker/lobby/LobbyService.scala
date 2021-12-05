package mixzpoker.lobby

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.Stream
import tofu.logging.Logging
import tofu.syntax.logging._

import scala.concurrent.duration._
import mixzpoker.AppError
import mixzpoker.domain.Token
import mixzpoker.domain.lobby.LobbyOutputMessage
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.game.poker.PokerService
import mixzpoker.lobby.LobbyError.GameIsAlreadyStarted
import mixzpoker.user.User

//todo make topic/queue per user?
trait LobbyService[F[_]] {
  def topic: Topic[F, LobbyOutputMessage]
  def queue: Queue[F, LobbyMessageContext]

  def run: F[Unit]
}

object LobbyService {
  def of[F[_]: Concurrent: Timer: Logging](
    repository: LobbyRepository[F], pokerService: PokerService[F]
  ): F[LobbyService[F]] = for {
    _queue <- Queue.unbounded[F, LobbyMessageContext]
    _topic <- Topic[F, LobbyOutputMessage](KeepAlive)
  } yield new LobbyService[F] {
    override def topic: Topic[F, LobbyOutputMessage] = _topic
    override def queue: Queue[F, LobbyMessageContext] = _queue

    override def run: F[Unit] = {
      val keepAlive = Stream
        .awakeEvery[F](30.seconds)
        .map(_ => KeepAlive)
        .through(topic.publish)

      val processingStream = queue
        .dequeue
        .evalTap(event => info"Receive an event: ${event.toString}")
        .evalMap(process)
        .flatten
        .evalTap(msg => info"Response with msg: ${msg.toString}")
        .through(topic.publish)

      info"LobbyService started!" *> Stream(keepAlive, processingStream).parJoinUnbounded.compile.drain
    }

    def process(e: LobbyMessageContext): F[Stream[F, LobbyOutputMessage]] = {
      e.message match {
        case Join(buyIn)          => joinLobby(e.lobbyName, e.user, buyIn)
        case Leave                => leaveLobby(e.lobbyName, e.user)
        case Ready                => updatePlayerReadiness(e.lobbyName, e.user, readiness = true)
        case NotReady             => updatePlayerReadiness(e.lobbyName, e.user, readiness = false)
        case ChatMessage(message) => chat(message, e.user)
      }
    }
      .recover { case err: AppError => Stream(ErrorMessage(err.toString))}
      .handleErrorWith { err => error"Error occurred! ${err.toString}" as Stream[F, LobbyOutputMessage]() }

    def startGame(lobby: Lobby): F[Lobby] = for {
        gameId <- pokerService.createGame(lobby)
        lobby  <- lobby.startGame(gameId).liftTo[F]
      } yield lobby

    def updatePlayerReadiness(lobbyName: LobbyName, user: User, readiness: Boolean): F[Stream[F, LobbyOutputMessage]] =
      for {
        lobby <- repository.get(lobbyName)
        _     <- Either.cond(!lobby.isStarted, (), GameIsAlreadyStarted).liftTo[F]
        lobby <- lobby.updatePlayerReadiness(user, readiness).liftTo[F]
        lobby <- if (readiness && lobby.satisfiesSettings && lobby.players.forall(_.ready))
                  startGame(lobby)
                 else
                  lobby.pure[F]
        _     <- repository.save(lobby)
      } yield Stream(LobbyState(lobby = lobby.dto))

    def joinLobby(lobbyName: LobbyName, user: User, buyIn: Token): F[Stream[F, LobbyOutputMessage]] = for {
      lobby <- repository.get(lobbyName)
      lobby <- lobby.joinPlayer(user, buyIn).liftTo[F]
      _     <- repository.save(lobby)
    } yield Stream(LobbyState(lobby = lobby.dto))

    def leaveLobby(lobbyName: LobbyName, user: User): F[Stream[F, LobbyOutputMessage]] = for {
      lobby <- repository.get(lobbyName)
      lobby <- lobby.leavePlayer(user).liftTo[F]
      _     <- repository.save(lobby)
    } yield Stream(LobbyState(lobby = lobby.dto))

    def chat(message: String, user: User): F[Stream[F, LobbyOutputMessage]] =
      Stream[F, LobbyOutputMessage](ChatMessageFrom(message, user.dto): LobbyOutputMessage).pure[F]
  }
}
