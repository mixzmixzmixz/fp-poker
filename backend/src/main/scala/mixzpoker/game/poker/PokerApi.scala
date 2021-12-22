package mixzpoker.game.poker

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import fs2.Stream
import fs2.concurrent.{Queue, Topic}
import org.http4s.{AuthedRoutes, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.server.websocket.WebSocketBuilder
import io.circe.syntax._
import io.circe.parser.decode
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.auth.AuthService
import mixzpoker.domain.chat.{ChatInputMessage, ChatOutputMessage}
import mixzpoker.domain.game.{GameError, GameEventId, GameId}
import mixzpoker.domain.game.poker.{PokerEventContext, PokerOutputMessage}
import mixzpoker.domain.game.poker.PokerEvent.PokerPlayerEvent
import mixzpoker.domain.user.User
import mixzpoker.domain.lobby.Lobby._
import mixzpoker.lobby.LobbyRepository


//todo pokerApp is going to be separate service with its own http api
// todo check user rights
class PokerApi[F[_]: Sync: Logging: GenUUID](
  pokerService: PokerService[F],
  lobbyRepository: LobbyRepository[F],
  authService: AuthService[F]
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
    //todo as resource
    def processInput(queue: Queue[F, PokerEventContext], userRef: Ref[F, Option[User]])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected" //todo process disconnects
      }.evalMapAccumulate(none[User]) {
        case (Some(user), text)  => (user.some, text).pure[F]
        case (None, token) =>
          authService
            .getAuthUser(token)
            .map {
              _.fold((none[User], token)) { user => (user.some, token) }
            }.flatTap {
              case (maybeUser, _) => userRef.update(_ => maybeUser)
            }
      }.collect {
        case (Some(user), text) => (user, decode[PokerPlayerEvent](text).leftMap(_.toString))
      }.evalTap {
        case (user, Left(err))  => error"$err"
        case (user, Right(msg)) => info"Event: GameId=${gameId.toString}, message=${msg.toString}"
      }.collect {
        case (user, Right(event)) => (user, event)
      }.evalMap { case (user, event) =>
        GenUUID[F].randomUUID.map(uuid =>
          PokerEventContext(id = GameEventId.fromUUID(uuid), gameId, user.id.some, event)
        )
      }.through(queue.enqueue)
    }

    for {
      _         <- pokerService.ensureExists(gameId)
      topic     <- pokerService.getTopic(gameId)
      userRef   <- Ref.of[F, Option[User]](None)
      toClient  =  topic
        .subscribe(1000).evalFilter {
          case PokerOutputMessage.ErrorMessage(Some(id), _) => userRef.get.map(_.fold(false)(_.id == id))
          case _  => true.pure[F]
        }.map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(pokerService.queue, userRef))
    } yield ws
  }.recoverWith {
    case GameError.NoSuchGame => NotFound()
    case _                    => InternalServerError()
  }

  private def chatWS(gameId: GameId): F[Response[F]] = {
    def processInput(topic: Topic[F, ChatOutputMessage])(wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] =
      wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.evalMapAccumulate(none[User]) {
        case (Some(user), text)  => (user.some, text).pure[F]
        case (None, token) =>
          authService
            .getAuthUser(token)
            .map(_.fold((none[User], token)) { user => (user.some, token) })
      }.collect {
        case (Some(user), text) => (user, decode[ChatInputMessage](text).leftMap(_.toString))
      }.evalTap {
        case (user, Left(err))  => error"$err"
        case (user, Right(msg)) => info"ChatMsg: GameId=${gameId.toString}, message=${msg.toString}"
      }.collect {
        case (user, Right(ChatInputMessage.ChatMessage(msg))) => ChatOutputMessage.ChatMessageFrom(msg, user)
      }.through(topic.publish)

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
      _       <- info"Get lobbies: ${lobbies.asJson.spaces2}"
      resp    <- Ok(lobbies.asJson)
    } yield resp
  }
}
