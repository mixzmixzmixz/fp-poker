package mixzpoker.user

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import tofu.generate.GenRandom
import mixzpoker.domain.user.UserError._
import mixzpoker.domain.user.{User, UserError, UserId, UserName, UserPassword}


trait UserRepository[F[_]] {
  def create(name: UserName, password: UserPassword): F[Either[UserError, User]]
  def get(name: UserName): F[Option[User]]
  def save(user: User): F[Unit]
  def checkPassword(password: UserPassword, name: UserName): F[Option[Boolean]]
}

object UserRepository {
  def inMemory[F[_]: Sync: GenRandom]: F[UserRepository[F]] = for {
    store <- Ref.of[F, Map[UserName, User]](Map.empty)
  } yield new UserRepository[F] {
    override def get(name: UserName): F[Option[User]] =
      store.get.map(_.get(name))

    override def save(user: User): F[Unit] =
      store.update { _.updated(user.name, user) }

    override def create(name: UserName, password: UserPassword): F[Either[UserError, User]] =
      GenRandom[F]
        .nextInt(100000).map(i => UserId.fromInt(i+1))
        .map(uid => User.create(uid, name, password))
        .flatMap {
          case Left(err) => err.asLeft[User].pure[F]
          case Right(user) =>
            store.getAndUpdate { m =>
              if (m.contains(user.name)) m else m.updated(user.name, user)
            }.map(oldM => Either.cond(!oldM.contains(user.name), user, UserAlreadyExist))
        }

    override def checkPassword(password: UserPassword, name: UserName): F[Option[Boolean]] =
      get(name).map(_.map(_.password == password))

  }

}