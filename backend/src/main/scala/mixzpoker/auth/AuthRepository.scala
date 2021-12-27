package mixzpoker.auth

import cats.implicits._
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.effect.concurrent.Ref
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import tofu.generate.{GenRandom, GenUUID}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.domain.auth.AuthToken
import mixzpoker.domain.user.UserName


trait AuthRepository[F[_]] {
  def getUserName(authToken: AuthToken): F[Option[UserName]]
  def addToken(name: UserName): F[AuthToken]
  def deleteToken(authToken: AuthToken): F[Unit]
}

object AuthRepository {
  def inMemory[F[_]: Sync]: F[AuthRepository[F]] = for {
    store <- Ref.of[F, Map[AuthToken, UserName]](Map.empty)
  } yield new AuthRepository[F] {
    override def getUserName(authToken: AuthToken): F[Option[UserName]] =
      store.get.map(_.get(authToken))

    override def addToken(name: UserName): F[AuthToken] = for {
      token <- GenUUID[F].randomUUID.map(AuthToken.fromUUID)
      _     <- store.update(_.updated(token, name))
    } yield token

    override def deleteToken(authToken: AuthToken): F[Unit] =
      store.update { _ - authToken }

  }

  def ofRedis[F[_]: Concurrent: ContextShift: GenRandom: Logging: Log](
    uri: String = "redis://localhost:6380"
  ): Resource[F, AuthRepository[F]] =
    for {
      redis <- Redis[F].utf8(uri).evalTap { redis =>
        redis.info.flatMap { _.get("redis_version").traverse_ { v => info"Connected to Redis $v" } }
      }
    } yield new AuthRepository[F] {
      private def key(authToken: AuthToken): String =
        s"table#user#tokens#${authToken.toString}"

      override def getUserName(authToken: AuthToken): F[Option[UserName]] =
        redis
          .get(key(authToken))
          .flatTap {
            case Some(username) => info"Got username=$username for token=${authToken.toString}"
            case None           => info"No username for token=${authToken.toString}"
          }
          .map(_.flatMap(UserName.fromString))

      override def addToken(name: UserName): F[AuthToken] = for {
        //todo add expire time
        token <- GenUUID[F].randomUUID.map(AuthToken.fromUUID)
        _     <- redis.set(key(token), name.toString)
      } yield token

      override def deleteToken(authToken: AuthToken): F[Unit] =
        redis.del(key(authToken)) as ()
    }
}