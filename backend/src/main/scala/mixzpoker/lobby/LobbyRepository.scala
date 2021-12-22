package mixzpoker.lobby

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import mixzpoker.domain.game.GameType
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.domain.lobby.{Lobby, LobbyName}
import mixzpoker.domain.user.User


trait LobbyRepository[F[_]] {
  def create(name: LobbyName, owner: User, gameType: GameType): F[Boolean]
  def list(): F[List[Lobby]]
  def listWithGameStarted: F[List[Lobby]]
  def get(name: LobbyName): F[Option[Lobby]]
  def save(lobby: Lobby): F[Unit]
  def delete(name: LobbyName): F[Unit]
  def exists(name: LobbyName): F[Boolean]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store  <- Ref.of[F, Map[LobbyName, Lobby]](Map.empty)
  } yield new LobbyRepository[F] {

    override def create(name: LobbyName, owner: User, gameType: GameType): F[Boolean] = {
      val settings = gameType match {
        case GameType.Poker => PokerSettings.defaults
      }
      val lobby = Lobby(name, owner, List(), gameType, settings)

      store.getAndUpdate { m =>
        if (m.contains(name)) m
        else m.updated(name, lobby)
      }.map(m => !m.contains(name))
    }

    override def list(): F[List[Lobby]] =
      store.get.map(_.values.toList)

    override def listWithGameStarted: F[List[Lobby]] =
      store.get.map(_.values.toList.filter(_.gameId.isDefined))

    override def get(name: LobbyName): F[Option[Lobby]] =
      store.get.map(_.get(name))

    override def save(lobby: Lobby): F[Unit] =
      store.update { _.updated(lobby.name, lobby) }

    override def delete(name: LobbyName): F[Unit] =
      store.update { _ - name }

    override def exists(name: LobbyName): F[Boolean] =
      get(name).map(_.isDefined)

  }
}
