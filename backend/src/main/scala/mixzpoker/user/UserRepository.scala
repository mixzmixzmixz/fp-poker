package mixzpoker.user

import cats.implicits._
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.effect.concurrent.Ref
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import tofu.generate.GenRandom
import tofu.logging.Logging
import tofu.syntax.logging._
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

  def ofRedis[F[_]: Concurrent: ContextShift: GenRandom: Logging: Log](
    uri: String = "redis://localhost:6380"
  ): Resource[F, UserRepository[F]] = for {
    redis <- Redis[F].utf8(uri).evalTap { redis =>
      redis.info.flatMap {
        _.get("redis_version").traverse_ { v => info"Connected to Redis $v" }
      }
    }
  } yield new UserRepository[F] {
    //this uses userName as a key, since I store users in hashmaps
    override def create(name: UserName, password: UserPassword): F[Either[UserError, User]] = {
      GenRandom[F]
        .nextInt(100000)
        .map(i => UserId.fromInt(i+1))
        .map(uid => User.create(uid, name, password))
        .flatMap {
          case Left(err)   => err.asLeft[User].pure[F]
          case Right(user) => save(user).map(_ => user.asRight[UserError])
        }
    }

    override def get(name: UserName): F[Option[User]] =
      redis
        .hmGet(usersKey(name), "id", "username", "password", "balance")
        .map { map =>
          (map.get("id"), map.get("password"), map.get("username"), map.get("balance"))
            .mapN[Option[User]] { case (id, pw, name, balance) =>
              (UserId.fromString(id), UserPassword.fromString(pw).some, UserName.fromString(name), balance.toIntOption)
                .mapN { case (id, pw, name, balance) =>
                  User.create(id, name, pw, balance).toOption
                }.flatten
            }.flatten
        }

    override def save(user: User): F[Unit] =
      redis.hmSet(usersKey(user.name), Map(
        "id" -> user.id.toString,
        "username" -> user.name.toString,
        "password" -> user.password.toString,
        "balance" -> user.amount.toString
      ))

    override def checkPassword(password: UserPassword, name: UserName): F[Option[Boolean]] =
      redis.hmGet(usersKey(name), "password").map(_.get("password").map(_ == password.toString))

    def usersKey(name: UserName): String =
      s"table#users#${name.toString}"

  }

}