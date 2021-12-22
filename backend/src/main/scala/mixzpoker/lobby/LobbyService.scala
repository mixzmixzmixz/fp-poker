package mixzpoker.lobby

import cats.effect.syntax.all._
import cats.effect.{Concurrent, Resource, Timer}
import cats.implicits._
import fs2.{Pipe, Stream}
import fs2.concurrent.{Queue, Topic}
import tofu.logging.Logging
import tofu.syntax.logging._
import org.http4s.websocket.WebSocketFrame.Text
import io.circe.syntax._
import io.circe.parser.decode
import mixzpoker.chat.ChatService
import mixzpoker.domain.Token
import mixzpoker.domain.game.GameType
import mixzpoker.domain.lobby.{Lobby, LobbyError, LobbyInputMessage, LobbyName, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.domain.lobby.LobbyError._
import mixzpoker.domain.user.User
import mixzpoker.game.poker.PokerService


trait LobbyService[F[_]] {
  def create(name: LobbyName, owner: User, gameType: GameType): F[Boolean]
  def runBackground: Resource[F, F[Unit]]
  def toClient(name: LobbyName): Stream[F, Text]
  def fromClientPipe(name: LobbyName): Stream[F, (Option[User], String)] => Stream[F, Unit]
  def chatPipes(name: LobbyName): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]]
}

object LobbyService {
  def of[F[_]: Concurrent: Timer: Logging](
    repository: LobbyRepository[F],
    pokerService: PokerService[F]
  ): F[LobbyService[F]] = {
    for {
      queue       <- Queue.unbounded[F, LobbyMessageContext]
      ln          <- LobbyName.fromString("asdasd").toRight(NoSuchLobby).liftTo[F] // todo fix
      topic       <- Topic[F, (LobbyName, LobbyOutputMessage)]((ln, KeepAlive))
      chatService <- ChatService.of[F, LobbyName]
    } yield new LobbyService[F] {
      override def create(name: LobbyName, owner: User, gameType: GameType): F[Boolean] =
        repository
          .create(name, owner, gameType)
          .flatMap { cond =>
            if (cond) chatService.create(name) as cond
            else cond.pure[F]
          }

      override def chatPipes(name: LobbyName): F[Option[(Stream[F, Text], Pipe[F, (Option[User], String), Unit])]] =
        chatService.pipes(name)

      override def runBackground: Resource[F, F[Unit]] =
        queue
          .dequeue
          .evalTap(event => info"Receive an event: ${event.toString}")
          .evalMap(process)
          .collect { case Some(value) => value }
          .evalTap { case (name, msg) => info"Response(lobby=${name.toString}) with msg: ${msg.toString}" }
          .through(topic.publish)
          .compile
          .drain
          .background

      def process(e: LobbyMessageContext): F[Option[(LobbyName, LobbyOutputMessage)]] = {
        e.message match {
          case Join(buyIn) =>
            joinLobby(e.lobbyName, e.user, buyIn).map {
              case Left(err) => ErrorMessage(err.toString).some
              case Right(lobby) => LobbyState(lobby).some
            }

          case Leave =>
            leaveLobby(e.lobbyName, e.user).map {
              case Left(err) => ErrorMessage(err.toString).some
              case Right(lobby) => LobbyState(lobby).some
            }

          case Ready =>
            updatePlayerReadiness(e.lobbyName, e.user, readiness = true).flatMap {
              case Some(lobby) if lobby.satisfiesSettings && lobby.players.forall(_.ready) =>
                startGame(lobby).map(LobbyState).map(_.some: Option[LobbyOutputMessage])

              case Some(lobby) => (LobbyState(lobby): LobbyOutputMessage).some.pure[F]
              case _           => none[LobbyOutputMessage].pure[F]
            }

          case NotReady =>
            updatePlayerReadiness(e.lobbyName, e.user, readiness = false)
              .map(_.map(LobbyState))
        }
      }.map( _.map(lom => (e.lobbyName, lom)))
//      }.recover { case err: AppError => (e.lobbyName, ErrorMessage(err.toString)) }
      //todo do I need to handle errors here?
      //      .handleErrorWith { err => error"Error occurred! ${err.toString}" as (e.lobbyName, KeepAlive) }

      def startGame(lobby: Lobby): F[Lobby] =
        if (lobby.gameId.isDefined) lobby.pure[F]
        else
          pokerService
            .createGame(lobby)
            .map(gid => lobby.copy(gameId = gid.some))
            .flatTap(repository.save)

      def updatePlayerReadiness(lobbyName: LobbyName, user: User, readiness: Boolean): F[Option[Lobby]] =
        repository
          .get(lobbyName)
          .map {
            case Some(lobby) if !lobby.isStarted => lobby.updatePlayerReadiness(user, readiness)
            case _                               => none[Lobby]
          }.flatTap {
            case Some(lobby) => repository.save(lobby)
            case None        => ().pure[F]
          }

      def joinLobby(lobbyName: LobbyName, user: User, buyIn: Token): F[Either[LobbyError, Lobby]] =
        repository
          .get(lobbyName)
          .map {
            case Some(lobby) => lobby.joinPlayer(user, buyIn)
            case None        => NoSuchLobby.asLeft[Lobby]
          }.flatTap {
            case Right(lobby) => repository.save(lobby)
            case _            => ().pure[F]
          }

      def leaveLobby(lobbyName: LobbyName, user: User): F[Either[LobbyError, Lobby]] =
        repository
          .get(lobbyName)
          .map {
            case Some(lobby) => lobby.leavePlayer(user)
            case None        => NoSuchLobby.asLeft[Lobby]
          }.flatTap {
            case Right(lobby) => repository.save(lobby)
            case _            => ().pure[F]
          }

      override def toClient(name: LobbyName): Stream[F, Text] =
        topic
          .subscribe(1000)
          .collect { case (lobbyName, message) if name == lobbyName => message }
          .map(msg => Text(msg.asJson.noSpaces))

      override def fromClientPipe(name: LobbyName): Pipe[F, (Option[User], String), Unit] =
        _.collect {
          case (Some(user), text) => (user, decode[LobbyInputMessage](text).leftMap(_.toString))
        }.evalTap {
          case (user, Left(err))  => error"$err"
          case (user, Right(msg)) => info"Event: Lobby=${name.value}, message=${msg.toString}"
        }.collect {
          case (user, Right(msg)) => LobbyMessageContext(user, name, msg)
        }.through(queue.enqueue)
    }

    }
}
