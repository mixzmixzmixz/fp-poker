package mixzpoker.lobby


import cats.implicits._
import cats.effect.Sync
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.game.poker.PokerSettings
import mixzpoker.game.poker.PokerEvent.CreateGameEvent
import mixzpoker.game.{EventId, GameId, GameType}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.user.User


class LobbyApi[F[_]: Sync: Logging](
  lobbyRepository: LobbyRepository[F], broker: Broker[F]
) {
  val dsl: Http4sDsl[F] = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {

    case GET -> Root / "lobby" as user => getLobbies(user)

    case GET -> Root / "lobby" / name as user => getLobby(name)

    case req @ POST -> Root / "lobby" / "create" as user =>  createLobby(req.req, user)

    case req @ POST -> Root / "lobby" / name / "join" as user => joinLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "leave" as user => leaveLobby(req.req, name, user)

    case req @ POST -> Root / "lobby" / name / "start" as user => startGame(req.req, name, user)
  }

  private def getLobbies(user: User): F[Response[F]] = {
    //todo mb filter lobbies by User rights or smth
    // todo filter and pagination using query params
    for {
      _ <- info"Get lobbies req user:"
      lobbies <- lobbyRepository.getLobbiesList()
      _ <- info"Get lobbies: ${lobbies.asJson.spaces2}"
      resp <- Ok(lobbies.asJson)
    } yield resp
  }

  private def getLobby(name: String): F[Response[F]] = {
    for {
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      lobby <- lobbyRepository.getLobby(lobbyName)
      resp <- Ok(lobby.asJson)
    } yield resp
    //todo add handling erros
  }

  private def createLobby(req: Request[F], user: User): F[Response[F]] = {
    for {
      request <- req.decodeJson[LobbyDto.CreateLobbyRequest]
      lobby <- Lobby.of(request.name, user, request.gameType).liftTo[F]
      _ <- lobbyRepository.checkLobbyAlreadyExist(lobby.name)
      _ <- lobbyRepository.saveLobby(lobby)
      resp <- Created()
    } yield resp
  }

  private def joinLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      request <- req.decodeJson[LobbyDto.JoinLobbyRequest]
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      lobby <- lobbyRepository.getLobby(lobbyName)
      lobby2 <- lobby.joinPlayer(user, request.buyIn).liftTo[F]
      _ <- lobbyRepository.saveLobby(lobby2)
      resp <- Ok()
    } yield resp
  }

  private def leaveLobby(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      lobby <- lobbyRepository.getLobby(lobbyName)
      lobby2 <- lobby.leavePlayer(user).liftTo[F]
      _ <- lobbyRepository.saveLobby(lobby2)
      resp <- Ok()
    } yield resp
  }

  private def startGame(req: Request[F], name: String, user: User): F[Response[F]] = {
    for {
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      lobby <- lobbyRepository.getLobby(lobbyName)
      _ <- lobby.checkUserIsOwner(user).liftTo[F]
      gameId <- lobby.gameType match {
        case GameType.Poker => startPokerGame(lobby)
      }
      resp <- Ok(LobbyDto.CreateGameResponse(gameId.toString).asJson)
    } yield resp
  }

  private def startPokerGame(lobby: Lobby): F[GameId] = for {
    queue <- broker.getQueue("poker-game-topic")
    gameId <- GameId.fromRandom
    eventId <- EventId.fromRandom
    event = CreateGameEvent(
      id = eventId,
      gameId = gameId,
      settings = lobby.gameSettings.asInstanceOf[PokerSettings], // todo asInstanceOf
      users = lobby.users.map { case (user, token) => (user.id, token) }
    )
    _ <- queue.enqueue1(event.asJson.noSpaces)
  } yield gameId
}

