package mixzpoker.auth

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import AuthError._
import mixzpoker.user.UserName


trait AuthUserRepository[F[_]] {
  def getUserName(authToken: AuthToken): F[ErrOr[UserName]]
  def deleteToken(authToken: AuthToken): F[ErrOr[Unit]]
  def addToken(authToken: AuthToken, userName: UserName): F[ErrOr[Unit]]
}


object AuthUserRepository {

  case class InMemoryRepo[F[_]: Sync](store: Ref[F, Map[AuthToken, UserName]]) extends AuthUserRepository[F] {

    override def getUserName(authToken: AuthToken): F[ErrOr[UserName]] = for {
      userName <- store.get.map(_.get(authToken).toRight(NoSuchToken))
    } yield userName

    override def deleteToken(authToken: AuthToken): F[ErrOr[Unit]] = for {
      _ <- store.update { _ - authToken }
    } yield Right(())

    override def addToken(authToken: AuthToken, userName: UserName): F[ErrOr[Unit]] = for {
      _ <- store.update(_.updated(authToken, userName))
    } yield Right(())

  }

  def inMemory[F[_]: Sync]: F[AuthUserRepository[F]] = for {
    store <- Ref.of[F, Map[AuthToken, UserName]](Map())
  } yield InMemoryRepo(store)

}