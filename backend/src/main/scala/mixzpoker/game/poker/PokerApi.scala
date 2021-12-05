package mixzpoker.game.poker

import cats.implicits._
import cats.effect.Sync
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
import mixzpoker.user.User
import mixzpoker.domain.game.{GameEventId, GameId}
import mixzpoker.domain.game.poker.PokerInputMessage._
import mixzpoker.domain.game.poker.{PokerEvent, PokerEventContext, PokerInputMessage, PokerOutputMessage}
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
    case GET -> Root / "poker" / GameIdVar(gameId: GameId) / "ws" => pokerWS(gameId)
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "poker" as user => getPokerGames(user)
  }


  private def pokerWS(gameId: GameId): F[Response[F]] = {
    //todo close stream if the first msg is not Register()/ Not correct
    def processInput(queue: Queue[F, PokerEventContext], topic: Topic[F, PokerOutputMessage])
      (wsfStream: Stream[F, WebSocketFrame]): Stream[F, Unit] = {

      def processStreamInput(stream: Stream[F, String], user: User): Stream[F, PokerEventContext] =
        stream.map { text =>
          decode[PokerInputMessage](text).leftMap(_.toString)
        }.evalMap {
          case Left(err)     => info"$err".as(err.asLeft[PokerInputMessage])
          case Right(msg)    => info"Event: GameId=${gameId.toString}, message=${msg.toString}".as(msg.asRight[String])
        }.collect {
          case Right(msg)    => msg
        }.map[PokerEvent] {
          case Join(buyIn)   => PokerEvent.Join(user.id, buyIn)
          case Leave         => PokerEvent.Leave(user.id)
          case Fold          => PokerEvent.Fold(user.id)
          case Check         => PokerEvent.Check(user.id)
          case Call(amount)  => PokerEvent.Call(user.id, amount)
          case Raise(amount) => PokerEvent.Raise(user.id, amount)
          case AllIn(amount) => PokerEvent.AllIn(user.id, amount)
        }.evalMap { e =>
          UUID.randomUUID().pure[F].map(uuid =>
            PokerEventContext(id = GameEventId.fromUUID(uuid), gameId, e)
          )
        }

      val parsedWebSocketInput: Stream[F, PokerEventContext] = wsfStream.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.pull.uncons1.flatMap {
        case None                  => Pull.done: Pull[F, PokerEventContext, Unit]
        case Some((token, stream)) => Pull.eval(getAuthUser(token).flatMap {_.fold(
          err =>
            topic.publish1(PokerOutputMessage.ErrorMessage(s"unauthorized: ${err.toString}"))
              *> (Pull.done: Pull[F, PokerEventContext, Unit]).pure[F],
          user =>
            (processStreamInput(stream, user).pull.echo: Pull[F, PokerEventContext, Unit]).pure[F]
        )}).flatten
      }.stream

      (Stream.emits(Seq()) ++ parsedWebSocketInput).through(queue.enqueue)
    }

    for {
      _         <- pokerService.ensureExists(gameId)
      topic     <- pokerService.getTopic(gameId)
      toClient  =  topic.subscribe(1000).map(msg => Text(msg.asJson.noSpaces))
      ws        <- WebSocketBuilder[F].build(toClient, processInput(pokerService.queue, topic))
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
