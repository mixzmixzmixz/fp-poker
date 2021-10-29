package mixzpoker.lobby

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import LobbyError._


trait LobbyRepository[F[_]] {
  def getLobby(name: LobbyName): F[ErrOr[Lobby]]
  def saveLobby(lobby: Lobby): F[ErrOr[Unit]]
  def deleteLobby(name: LobbyName): F[ErrOr[Unit]]

  def checkLobbyAlreadyExist(name: LobbyName): F[ErrOr[Unit]]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store <- Ref.of(Map[LobbyName, Lobby]())
  } yield new LobbyRepository[F] {
    override def getLobby(name: LobbyName): F[ErrOr[Lobby]] = for {
      map <- store.get
      user = map.get(name).toRight(NoSuchLobby)
    } yield user

    override def saveLobby(lobby: Lobby): F[ErrOr[Unit]] = for {
      _ <- store.update { _.updated(lobby.name, lobby) }
    } yield Right(())

    override def deleteLobby(name: LobbyName): F[ErrOr[Unit]] = for {
      _ <- store.update { _ - name }
    } yield Right(())

    override def checkLobbyAlreadyExist(name: LobbyName): F[ErrOr[Unit]] = for {
      map <- store.get
      check = if (map.contains(name)) Left(LobbyAlreadyExist) else Right(())
    } yield check
  }

}
