package mixzpoker.lobby


import cats.implicits._
import cats.data.EitherT
import cats.effect.{Concurrent, Sync}
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._
import mixzpoker.AppError
import mixzpoker.game.{Game, GameRepository}
import mixzpoker.lobby.LobbyError.CreateGameError
import mixzpoker.user.User


class LobbyApi[F[_]: Sync: Concurrent](
  lobbyRepository: LobbyRepository[F], gameRepository: GameRepository[F]
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
      lobbyName <- EitherT(LobbyName.fromString(name).pure[F])
      lobby <- EitherT(lobbyRepository.getLobby(lobbyName))
      resp = Ok(lobby.asJson)
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def createLobby(req: Request[F], user: User): F[Response[F]] = {
    for {
      request <- EitherT.right(req.decodeJson[LobbyDto.CreateLobbyRequest])
      lobby <- EitherT(Lobby.of(request.name, user, request.gameType).pure[F])
      _ <- EitherT(lobbyRepository.checkLobbyAlreadyExist(lobby.name))
      _ <- EitherT(lobbyRepository.saveLobby(lobby))
      resp = Created()
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def joinLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      request <- EitherT.right(req.decodeJson[LobbyDto.JoinLobbyRequest])
      lobbyName <- EitherT(LobbyName.fromString(name).pure[F])
      lobby <- EitherT(lobbyRepository.getLobby(lobbyName))
      lobby2 <- EitherT(lobby.joinPlayer(user, request.buyIn).pure[F])
      _ <- EitherT(lobbyRepository.saveLobby(lobby2))
      resp = Ok()
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def leaveLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- EitherT(LobbyName.fromString(name).pure[F])
      lobby <- EitherT(lobbyRepository.getLobby(lobbyName))
      lobby2 <- EitherT(lobby.leavePlayer(user).pure[F])
      _ <- EitherT(lobbyRepository.saveLobby(lobby2))
      resp = Ok()
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten

  private def startGame(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- EitherT(LobbyName.fromString(name).pure[F])
      lobby <- EitherT(lobbyRepository.getLobby(lobbyName))
      _ <- EitherT(lobby.checkUserIsOwner(user).pure[F])
      newGame <- EitherT(Game.fromUsers(
        lobby.gameType, lobby.gameSettings, lobby.users
      ).leftMap[AppError](CreateGameError).pure[F])
      _ <- EitherT(gameRepository.saveGame(newGame, newGame.id)).leftMap[AppError](CreateGameError)
      resp = Ok(LobbyDto.CreateGameResponse(newGame.id.toString).asJson)
    } yield resp
  }.leftMap { err => Ok(err.toString) }.merge.flatten
}

