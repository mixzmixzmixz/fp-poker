package mixzpoker.lobby

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import mixzpoker.user.User
import mixzpoker.domain.game.GameType
import LobbyError._
import mixzpoker.domain.game.poker.PokerSettings


trait LobbyRepository[F[_]] {
  def create(name: String, owner: User, gameType: GameType): F[Unit]
  def list(): F[List[Lobby]]
  def get(name: LobbyName): F[Lobby]
  def save(lobby: Lobby): F[Unit]
  def delete(name: LobbyName): F[Unit]
  def exists(name: LobbyName): F[Boolean]
  def ensureExists(name: LobbyName): F[Unit]
  def ensureDoesNotExist(name: LobbyName): F[Unit]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store  <- Ref.of[F, Map[LobbyName, Lobby]](Map.empty)
  } yield new LobbyRepository[F] {

    override def create(name: String, owner: User, gameType: GameType): F[Unit] = for {
      lobbyName <- LobbyName.fromString(name).liftTo[F]
      settings  <- (gameType match {
                    case GameType.Poker => PokerSettings.create()
                  }).toRight(InvalidSettings).liftTo[F]
      _         <- ensureDoesNotExist(lobbyName)
      lobby     = Lobby(lobbyName, owner, List(), gameType, settings)
      _         <- save(lobby)
    } yield ()

    override def list(): F[List[Lobby]] =
      store.get.map(_.values.toList)

    override def get(name: LobbyName): F[Lobby] =
      store.get.flatMap(_.get(name).toRight(NoSuchLobby).liftTo[F])

    override def save(lobby: Lobby): F[Unit] =
      store.update { _.updated(lobby.name, lobby) }

    override def delete(name: LobbyName): F[Unit] =
      store.update { _ - name }

    override def ensureDoesNotExist(name: LobbyName): F[Unit] =
      store.get.flatMap { map => Either.cond(!map.contains(name), (), LobbyAlreadyExist).liftTo[F] }

    override def exists(name: LobbyName): F[Boolean] =
      store.get.map(_.contains(name))

    override def ensureExists(name: LobbyName): F[Unit] =
      exists(name).flatMap(b => Either.cond(b, (), NoSuchLobby).liftTo[F])
  }

}
