package mixzpoker.user

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import mixzpoker.domain.user.UserError._
import mixzpoker.domain.user.{User, UserName}


trait UserRepository[F[_]] {
  def get(name: UserName): F[User]
  def save(user: User): F[Unit]

  def checkUserAlreadyExist(name: UserName): F[Unit]
}

object UserRepository {
  def inMemory[F[_]: Sync]: F[UserRepository[F]] = for {
    store <- Ref.of[F, Map[UserName, User]](Map.empty)
  } yield new UserRepository[F] {
    override def get(name: UserName): F[User] =
      store.get.flatMap(_.get(name).toRight(NoSuchUser(name)).liftTo[F])

    override def save(user: User): F[Unit] =
      store.update { _.updated(user.name, user) }

    override def checkUserAlreadyExist(name: UserName): F[Unit] =
      store.get.map(m => Either.cond(m.contains(name), (), UserAlreadyExist(name)).liftTo[F])
  }

}