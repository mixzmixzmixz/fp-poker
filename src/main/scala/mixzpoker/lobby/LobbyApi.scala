package mixzpoker.lobby


import cats.implicits._
import cats.data.EitherT
import cats.effect.{Concurrent, Sync}
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._
import mixzpoker.AppError
import mixzpoker.game.poker.PokerSettings
import mixzpoker.game.poker.PokerEvent.CreateGameEvent
import mixzpoker.game.{EventId, GameId, GameType}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.user.User


class LobbyApi[F[_]: Sync: Concurrent](
  lobbyRepository: LobbyRepository[F], broker: Broker[F]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "lobby" / name as user => getLobby(name)

    case req @ POST -> Root / "lobby" / "create" as user =>  createLobby(req.req, user)

    case req @ POST -> Root / "lobby" / name / "join" as user => joinLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "leave" as user => leaveLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "start" as user => startGame(req.req, name, user)
  }

  private def getLobby(name: String): F[Response[F]] = {
    for {
      lobbyName <- EitherT.fromEither[F](LobbyName.fromString(name))
      lobby <- lobbyRepository.getLobby(lobbyName)
    } yield Ok(lobby.asJson)
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def createLobby(req: Request[F], user: User): F[Response[F]] = {
    for {
      request <- EitherT.right(req.decodeJson[LobbyDto.CreateLobbyRequest])
      lobby <- EitherT.fromEither[F](Lobby.of(request.name, user, request.gameType))
      _ <- lobbyRepository.checkLobbyAlreadyExist(lobby.name)
      _ <- lobbyRepository.saveLobby(lobby)
    } yield Created()
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def joinLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      request <- EitherT.right(req.decodeJson[LobbyDto.JoinLobbyRequest])
      lobbyName <- EitherT.fromEither[F](LobbyName.fromString(name))
      lobby <- lobbyRepository.getLobby(lobbyName)
      lobby2 <- EitherT.fromEither[F](lobby.joinPlayer(user, request.buyIn))
      _ <- lobbyRepository.saveLobby(lobby2)
    } yield Ok()
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def leaveLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- EitherT.fromEither[F](LobbyName.fromString(name))
      lobby <- lobbyRepository.getLobby(lobbyName)
      lobby2 <- EitherT.fromEither[F](lobby.leavePlayer(user))
      _ <- lobbyRepository.saveLobby(lobby2)
    } yield Ok()
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def startGame(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- EitherT.fromEither[F](LobbyName.fromString(name))
      lobby <- lobbyRepository.getLobby(lobbyName)
      _ <- EitherT.fromEither[F](lobby.checkUserIsOwner(user))
      gameId <- lobby.gameType match {
        case GameType.Poker => startPokerGame(lobby)
      }
    } yield Ok(LobbyDto.CreateGameResponse(gameId.toString).asJson)
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def startPokerGame(lobby: Lobby): EitherT[F, AppError, GameId] = for {
    queue <- broker.getQueue("gamesTopic")
    gameId <- EitherT.right(GameId.fromRandom)
    eventId <- EitherT.right(EventId.fromRandom)
    event = CreateGameEvent(
      id = eventId,
      gameId = gameId,
      settings = lobby.gameSettings.asInstanceOf[PokerSettings], // todo asInstanceOf
      users = lobby.users.map { case (user, token) => (user.id, token) }
    )
    _ <- EitherT.right(queue.enqueue1(event.asJson.noSpaces))
  } yield gameId
}

