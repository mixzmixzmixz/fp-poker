package mixzpoker.lobby

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import LobbyError._


trait LobbyRepository[F[_]] {
  def getLobbiesList(): F[List[Lobby]]
  def getLobby(name: LobbyName): F[Lobby]
  def saveLobby(lobby: Lobby): F[Unit]
  def deleteLobby(name: LobbyName): F[Unit]

  def checkLobbyAlreadyExist(name: LobbyName): F[Unit]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store <- Ref.of(Map[LobbyName, Lobby]())
  } yield new LobbyRepository[F] {
    override def getLobbiesList(): F[List[Lobby]] =
      store.get.map(_.values.toList)

    override def getLobby(name: LobbyName): F[Lobby] =
      store.get.flatMap(_.get(name).toRight(NoSuchLobby).liftTo[F])

    override def saveLobby(lobby: Lobby): F[Unit] =
      store.update { _.updated(lobby.name, lobby) }

    override def deleteLobby(name: LobbyName): F[Unit] =
      store.update { _ - name }

    override def checkLobbyAlreadyExist(name: LobbyName): F[Unit] =
      store.get.flatMap { map => Either.cond(!map.contains(name), (), LobbyAlreadyExist).liftTo[F] }

  }

}
