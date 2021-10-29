package mixzpoker.user

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import UserError._


trait UserRepository[F[_]] {
  def getUser(name: UserName): F[ErrOr[User]]
  def saveUser(user: User): F[ErrOr[Unit]]

  def checkUserAlreadyExist(name: UserName): F[ErrOr[Unit]]
}


object UserRepository {
  def inMemory[F[_]: Sync]: F[UserRepository[F]] = for {
    store <- Ref.of[F, Map[UserName, User]](Map())
  } yield new UserRepository[F] {
    override def getUser(name: UserName): F[ErrOr[User]] = for {
      map <- store.get
      user = map.get(name).toRight(NoSuchUser(name))
    } yield user

    override def saveUser(user: User): F[ErrOr[Unit]] = for {
      _ <- store.update { _.updated(user.name, user) }
    } yield Right(())

    override def checkUserAlreadyExist(name: UserName): F[ErrOr[Unit]] = for {
      map <- store.get
      check = if (map.contains(name)) Left(UserAlreadyExist(name)) else Right(())
    } yield check
  }

}