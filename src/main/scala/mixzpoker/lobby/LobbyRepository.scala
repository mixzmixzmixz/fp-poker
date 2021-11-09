package mixzpoker.lobby

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.data.EitherT

import LobbyError._


trait LobbyRepository[F[_]] {
  def getLobby(name: LobbyName): EitherT[F, LobbyError, Lobby]
  def saveLobby(lobby: Lobby): EitherT[F, LobbyError, Unit]
  def deleteLobby(name: LobbyName): EitherT[F, LobbyError, Unit]

  def checkLobbyAlreadyExist(name: LobbyName): EitherT[F, LobbyError, Unit]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store <- Ref.of(Map[LobbyName, Lobby]())
  } yield new LobbyRepository[F] {
    override def getLobby(name: LobbyName): EitherT[F, LobbyError, Lobby] =
      EitherT(store.get.map(_.get(name).toRight(NoSuchLobby)))

    override def saveLobby(lobby: Lobby): EitherT[F, LobbyError, Unit] =
      EitherT.right(store.update { _.updated(lobby.name, lobby) })

    override def deleteLobby(name: LobbyName): EitherT[F, LobbyError, Unit] =
      EitherT.right(store.update { _ - name })


    override def checkLobbyAlreadyExist(name: LobbyName): EitherT[F, LobbyError, Unit] =
      EitherT(store.get.map { map => Either.cond(!map.contains(name), (), LobbyAlreadyExist) })

  }

}
