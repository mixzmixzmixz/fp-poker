package mixzpoker.game.poker

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import fs2.{Pull, Stream}
import fs2.concurrent.{Queue, Topic}
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.server.websocket.WebSocketBuilder
import io.circe.syntax.EncoderOps
import io.circe.parser.decode
import tofu.logging.Logging
import tofu.syntax.logging._
import mixzpoker.auth.AuthError
import mixzpoker.domain.chat.{ChatInputMessage, ChatOutputMessage}
import mixzpoker.user.User
import mixzpoker.domain.game.{GameEventId, GameId}
import mixzpoker.domain.game.poker.{PokerEventContext, PokerOutputMessage}
import mixzpoker.domain.game.poker.PokerEvent.PokerPlayerEvent
import mixzpoker.game.GameError
import mixzpoker.lobby.LobbyRepository

import java.util.UUID

//todo pokerApp is going to be separate service with its own http api
// todo check user rights
class PokerApi[F[_]: Sync: Logging](
  pokerService: PokerService[F],
  lobbyRepository: LobbyRepository[F],
  getAuthUser: String => F[Either[AuthError, User]]
) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  object GameIdVar {
    def unapply(name: String): Option[GameId] = GameId.fromString(name).toOption
  }

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "poker" / GameIdVar(gameId: GameId) / "ws"          => pokerWS(gameId)
    case GET -> Root / "poker" / GameIdVar(gameId: GameId) / "chat" / "ws" => chatWS(gameId)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "poker" as user => getPokerGames(user)
  }

  private def pokerWS(gameId: GameId): F[Response[F]] = {
    def processInput(queue: Queue[F, PokerEventContext], userRef: Ref[F, Option[User]])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, PokerEventContext] =
        stream.map { text =>
          decode[PokerPlayerEvent](text).leftMap(_.toString)
        }.evalTap {
          case Left(err)  => error"$err"
          case Right(msg) => info"Event: GameId=${gameId.toString}, message=${msg.toString}"
        }.collect {
          case Right(msg) => msg
        }.evalMap { e =>
          { UUID.randomUUID() }.pure[F].map(uuid =>
            PokerEventContext(id = GameEventId.fromUUID(uuid), gameId, Some(user.id), e)
          )
        }

      wsfStream.collect {
        case Text(text, _) => text.trim
//        case Close(_)      => "disconnected" //todo process disconnects
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, PokerEventContext, Unit]
        case Some((token, stream)) =>
          Pull
            .eval(getAuthUser(token)
            .flatMap(eu => userRef.update(_ => eu.toOption) *> eu.pure[F])
            .map { _.fold(_ => Pull.done, user => processStreamInput(stream, user).pull.echo)
            }).flatten
      }.stream.through(queue.enqueue)
    }

    for {
      _         <- pokerService.ensureExists(gameId)
      topic     <- pokerService.getTopic(gameId)
      userRef   <- Ref.of[F, Option[User]](None)
      toClient  =  topic
        .subscribe(1000).evalFilter {
          case PokerOutputMessage.ErrorMessage(Some(id), _) => userRef.get.map(_.fold(false)(u => u.id == id))
          case _  => true.pure[F]
        }.map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(pokerService.queue, userRef))
    } yield ws
  }.recoverWith {
    case GameError.NoSuchGame => NotFound()
    case _                    => InternalServerError()
  }

  private def chatWS(gameId: GameId): F[Response[F]] = {
    def processInput(topic: Topic[F, ChatOutputMessage])(wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, ChatOutputMessage] =
        stream.map { text =>
          decode[ChatInputMessage](text).leftMap(_.toString)
        }.evalTap {
          case Left(err)  => error"$err"
          case Right(msg) => info"ChatMsg: GameId=${gameId.toString}, message=${msg.toString}"
        }.collect {
          case Right(msg) => msg
        }.map {
          case ChatInputMessage.ChatMessage(message) => ChatOutputMessage.ChatMessageFrom(message, user.dto)
        }

      wsfStream.collect {
        case Text(text, _) => text.trim
//        case Close(_)      => "disconnected"
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, ChatOutputMessage, Unit]
        case Some((token, stream)) => Pull.eval(getAuthUser(token).map {_.fold(
                                        err => Pull.done,
                                        user => processStreamInput(stream, user).pull.echo
                                      )}).flatten
      }.stream.through(topic.publish)
    }

    for {
      _         <- pokerService.ensureExists(gameId)
      topic     <- pokerService.getChatTopic(gameId)
      toClient  =  topic.subscribe(1000).map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(topic))
    } yield ws
  }.recoverWith {
    case GameError.NoSuchGame => NotFound()
    case _                    => InternalServerError()
  }

  private def getPokerGames(user: User): F[Response[F]] = {
    // todo mb filter games by user rights or smth
    // todo filter and pagination using query params
    for {
      _       <- info"Get Poker Games"
      lobbies <- lobbyRepository.listWithGameStarted
      _       <- info"Get lobbies: ${lobbies.map(_.dto).asJson.spaces2}"
      resp    <- Ok(lobbies.map(_.dto).asJson)
    } yield resp
  }
}
