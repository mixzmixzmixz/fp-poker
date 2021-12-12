package mixzpoker.lobby

import cats.effect.Sync
import cats.implicits._
import fs2.{Pull, Stream}
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

import mixzpoker.auth.AuthError
import mixzpoker.domain.chat.{ChatInputMessage, ChatOutputMessage}
import mixzpoker.user.User
import mixzpoker.domain.lobby.{LobbyDto, LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyInputMessage._
import mixzpoker.game.GameError


class LobbyApi[F[_]: Sync: Logging](
  lobbyService: LobbyService[F],
  lobbyRepository: LobbyRepository[F],
  getAuthUser: String => F[Either[AuthError, User]]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  object LobbyNameVar {
    def unapply(name: String): Option[LobbyName] = LobbyName.fromString(name).toOption
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
    def processInput(queue: Queue[F, LobbyMessageContext], topic: Topic[F, (LobbyName, LobbyOutputMessage)])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, LobbyMessageContext] =
        stream.map { text =>
          decode[LobbyInputMessage](text).leftMap(_.toString)
        }.evalMap {
          case Left(err)   => info"$err".as(err.asLeft[LobbyInputMessage])
          case Right(msg)  => info"Event: Lobby=${name.value}, message=${msg.toString}".as(msg.asRight[String])
        }.collect {
          case Right(msg)  => LobbyMessageContext(user, name, msg)
        }

      val parsedWebSocketInput: Stream[F, LobbyMessageContext] = wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, LobbyMessageContext, Unit]
        case Some((token, stream)) => Pull.eval(getAuthUser(token).flatMap {_.fold(
          err =>
            topic.publish1((name, LobbyOutputMessage.ErrorMessage(s"unauthorized: ${err.toString}")))
              *> (Pull.done: Pull[F, LobbyMessageContext, Unit]).pure[F],
          user =>
            (processStreamInput(stream, user).pull.echo: Pull[F, LobbyMessageContext, Unit]).pure[F]
        )}).flatten
      }.stream

      (Stream.emits(Seq()) ++ parsedWebSocketInput).through(queue.enqueue)
    }

    for {
      _         <- lobbyRepository.ensureExists(name)
      toClient  =  lobbyService
                    .topic
                    .subscribe(1000)
                    .collect { case (lobbyName, message) if name.value == lobbyName.value => message }
                    .map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(lobbyService.queue, lobbyService.topic))
    } yield ws
  }.recoverWith {
    case LobbyError.NoSuchLobby => NotFound()
    case _                      => InternalServerError()
  }

  private def chatWS(name: LobbyName): F[Response[F]] = {
    def processInput(topic: Topic[F, (LobbyName, ChatOutputMessage)])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, (LobbyName, ChatOutputMessage)] =
        stream.map { text =>
          decode[ChatInputMessage](text).leftMap(_.toString)
        }.evalTap {
          case Left(err)  => error"$err"
          case Right(msg) => info"ChatMsg: name=${name.toString}, message=${msg.toString}"
        }.collect {
          case Right(msg) => msg
        }.map {
          case ChatInputMessage.ChatMessage(message) => (name, ChatOutputMessage.ChatMessageFrom(message, user.dto))
        }

      //todo simplify. all the messages go through the pipe
      val parsedWebSocketInput: Stream[F, (LobbyName, ChatOutputMessage)] = wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, (LobbyName, ChatOutputMessage), Unit]
        case Some((token, stream)) => Pull.eval(getAuthUser(token).flatMap {_.fold(
          err =>
            topic.publish1((name, ChatOutputMessage.ErrorMessage(s"unauthorized: ${err.toString}")))
              *> (Pull.done: Pull[F, (LobbyName, ChatOutputMessage), Unit]).pure[F],
          user =>
            (processStreamInput(stream, user).pull.echo: Pull[F, (LobbyName, ChatOutputMessage), Unit]).pure[F]
        )}).flatten
      }.stream

      (Stream.emits(Seq()) ++ parsedWebSocketInput).through(topic.publish)
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
      _       <- info"Get lobbies: ${lobbies.map(_.dto).asJson.spaces2}"
      resp    <- Ok(lobbies.map(_.dto).asJson)
    } yield resp
  }

  private def getLobby(name: LobbyName): F[Response[F]] = for {
    lobby <- lobbyRepository.get(name)
    resp  <- Ok(lobby.dto.asJson)
  } yield resp

  private def createLobby(request: Request[F], user: User): F[Response[F]] = for {
    req  <- request.decodeJson[LobbyDto.CreateLobbyRequest]
    _    <- lobbyRepository.create(req.name, user, req.gameType)
    resp <- Created()
  } yield resp
}

