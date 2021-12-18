package mixzpoker.lobby

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.domain.{AppError, Token}
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.lobby.{Lobby, LobbyName, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.domain.lobby.LobbyError._
import mixzpoker.domain.user.User
import mixzpoker.game.poker.PokerService



//todo make topic/queue per user?
trait LobbyService[F[_]] {
  def queue: Queue[F, LobbyMessageContext]

  def topic: Topic[F, (LobbyName, LobbyOutputMessage)]
  def chatTopic: Topic[F, (LobbyName, ChatOutputMessage)]

  def run: F[Unit]
}

object LobbyService {
  def of[F[_]: Concurrent: Timer: Logging](
    repository: LobbyRepository[F],
    pokerService: PokerService[F]
  ): F[LobbyService[F]] = for {
    _queue     <- Queue.unbounded[F, LobbyMessageContext]
    ln         <- LobbyName.fromString("asdasd").toRight(NoSuchLobby).liftTo[F] // todo fix
    _topic     <- Topic[F, (LobbyName, LobbyOutputMessage)]((ln, KeepAlive))
    _chatTopic <- Topic[F, (LobbyName, ChatOutputMessage)]((ln, ChatOutputMessage.KeepAlive))
  } yield new LobbyService[F] {

    override def queue: Queue[F, LobbyMessageContext] = _queue
    override def topic: Topic[F, (LobbyName, LobbyOutputMessage)] = _topic
    override def chatTopic: Topic[F, (LobbyName, ChatOutputMessage)] = _chatTopic

    override def run: F[Unit] = {
//      val keepAlive = Stream
//        .awakeEvery[F](30.seconds)
//        .map(_ => KeepAlive)
//        .through(topic.publish)

      val processingStream = queue
        .dequeue
        .evalTap(event => info"Receive an event: ${event.toString}")
        .evalMap(process)
        .evalTap { case (name, msg) => info"Response(lobby=${name.toString}) with msg: ${msg.toString}" }
        .through(topic.publish)


      info"LobbyService started!" *> processingStream.compile.drain
    }

    def process(e: LobbyMessageContext): F[(LobbyName, LobbyOutputMessage)] = {
      e.message match {
        case Join(buyIn)          => joinLobby(e.lobbyName, e.user, buyIn)
        case Leave                => leaveLobby(e.lobbyName, e.user)
        case Ready                => updatePlayerReadiness(e.lobbyName, e.user, readiness = true)
        case NotReady             => updatePlayerReadiness(e.lobbyName, e.user, readiness = false)
      }
    }
      .recover { case err: AppError => (e.lobbyName, ErrorMessage(err.toString)) }
    //todo do I need to handle errors here?
//      .handleErrorWith { err => error"Error occurred! ${err.toString}" as (e.lobbyName, KeepAlive) }

    def startGame(lobby: Lobby): F[Lobby] = for {
        gameId <- pokerService.createGame(lobby)
        lobby  <- lobby.startGame(gameId).liftTo[F]
      } yield lobby

    def updatePlayerReadiness(lobbyName: LobbyName, user: User, readiness: Boolean): F[(LobbyName, LobbyOutputMessage)] =
      for {
        lobby <- repository.get(lobbyName)
        _     <- Either.cond(!lobby.isStarted, (), GameIsAlreadyStarted).liftTo[F]
        lobby <- lobby.updatePlayerReadiness(user, readiness).liftTo[F]
        lobby <- if (readiness && lobby.satisfiesSettings && lobby.players.forall(_.ready))
                  startGame(lobby)
                 else
                  lobby.pure[F]
        _     <- repository.save(lobby)
      } yield (lobbyName, LobbyState(lobby = lobby))

    def joinLobby(lobbyName: LobbyName, user: User, buyIn: Token): F[(LobbyName, LobbyOutputMessage)] = for {
      lobby <- repository.get(lobbyName)
      lobby <- lobby.joinPlayer(user, buyIn).liftTo[F]
      _     <- repository.save(lobby)
    } yield (lobbyName, LobbyState(lobby = lobby))

    def leaveLobby(lobbyName: LobbyName, user: User): F[(LobbyName, LobbyOutputMessage)] = for {
      lobby <- repository.get(lobbyName)
      lobby <- lobby.leavePlayer(user).liftTo[F]
      _     <- repository.save(lobby)
    } yield (lobbyName, LobbyState(lobby = lobby))
  }
}
