package mixzpoker.auth

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import AuthError._
import mixzpoker.user.UserName


trait AuthUserRepository[F[_]] {
  def getUserName(authToken: AuthToken): F[UserName]
  def deleteToken(authToken: AuthToken): F[Unit]
  def addToken(authToken: AuthToken, userName: UserName): F[Unit]
}

object AuthUserRepository {
  def inMemory[F[_]: Sync]: F[AuthUserRepository[F]] = for {
    store <- Ref.of[F, Map[AuthToken, UserName]](Map.empty)
  } yield new AuthUserRepository[F] {
    override def getUserName(authToken: AuthToken): F[UserName] =
      store.get.flatMap(_.get(authToken).toRight(NoSuchToken).liftTo[F])

    override def deleteToken(authToken: AuthToken): F[Unit] =
      store.update { _ - authToken }

    override def addToken(authToken: AuthToken, userName: UserName): F[Unit] =
      store.update(_.updated(authToken, userName))
  }
}