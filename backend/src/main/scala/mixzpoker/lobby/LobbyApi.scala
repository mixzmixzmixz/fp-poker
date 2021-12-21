package mixzpoker.lobby

import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import fs2.concurrent.{Queue, Topic}
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.{AuthedRoutes, HttpRoutes, Request, Response}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.auth.AuthService
import mixzpoker.domain.chat.{ChatInputMessage, ChatOutputMessage}
import mixzpoker.domain.game.GameError
import mixzpoker.domain.lobby.{LobbyError, LobbyInputMessage, LobbyName, LobbyOutputMessage, LobbyRequest}
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.domain.user.User


class LobbyApi[F[_]: Sync: Logging](
  lobbyService: LobbyService[F],
  lobbyRepository: LobbyRepository[F],
  authService: AuthService[F]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  object LobbyNameVar {
    def unapply(name: String): Option[LobbyName] = LobbyName.fromString(name)
  }

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "lobby" / LobbyNameVar(name) / "ws"          => lobbyWS(name)
    case GET -> Root / "lobby" / LobbyNameVar(name) / "chat" / "ws" => chatWS(name)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case       GET  -> Root / "lobby"                      as user => getLobbies(user)
    case       GET  -> Root / "lobby" / LobbyNameVar(name) as user => getLobby(name)
    case req @ POST -> Root / "lobby" / "create"           as user => createLobby(req.req, user)
  }

  private def lobbyWS(name: LobbyName): F[Response[F]] = {
    def processInput(queue: Queue[F, LobbyMessageContext])(wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {
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
        case (Some(user), text) => (user, decode[LobbyInputMessage](text).leftMap(_.toString))
      }.evalTap {
        case (user, Left(err))  => error"$err"
        case (user, Right(msg)) => info"Event: Lobby=${name.value}, message=${msg.toString}"
      }.collect {
        case (user, Right(msg)) => LobbyMessageContext(user, name, msg)
      }.through(queue.enqueue)
    }

    for {
      _         <- lobbyRepository.ensureExists(name)
      toClient  =  lobbyService
                    .topic
                    .subscribe(1000)
                    .collect { case (lobbyName, message) if name.value == lobbyName.value => message }
                    .map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.queue))
    } yield ws
  }.recoverWith {
    case LobbyError.NoSuchLobby => NotFound()
    case _                      => InternalServerError()
  }

  private def chatWS(name: LobbyName): F[Response[F]] = {
    def processInput(topic: Topic[F, (LobbyName, ChatOutputMessage)])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

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
        case (user, Right(msg)) => info"ChatMsg: name=${name.toString}, message=${msg.toString}"
      }.collect {
        case (user, Right(ChatInputMessage.ChatMessage(msg))) => (name, ChatOutputMessage.ChatMessageFrom(msg, user))
      }.through(topic.publish)
    }

    for {
      _         <- lobbyRepository.ensureExists(name)
      toClient  =  lobbyService
                    .chatTopic
                    .subscribe(1000)
                    .collect { case (lobbyName, message) if name.value == lobbyName.value => message }
                    .map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.chatTopic))
    } yield ws
  }.recoverWith {
    case GameError.NoSuchGame => NotFound()
    case _                    => InternalServerError()
  }


  private def getLobbies(user: User): F[Response[F]] = {
    //todo mb filter lobbies by user rights or smth
    // todo filter and pagination using query params
    for {
      _       <- info"Get lobbies req user:"
      lobbies <- lobbyRepository.list()
      _       <- info"Get lobbies: ${lobbies.asJson.spaces2}"
      resp    <- Ok(lobbies.asJson)
    } yield resp
  }

  private def getLobby(name: LobbyName): F[Response[F]] = for {
    lobby <- lobbyRepository.get(name)
    resp  <- Ok(lobby.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req  <- request.decodeJson[LobbyRequest.CreateLobbyRequest]
    _    <- lobbyRepository.create(req.name, user, req.gameType)
    resp <- Created()
  } yield resp
}

