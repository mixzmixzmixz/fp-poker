package mixzpoker.auth

import cats.data.OptionT
import cats.implicits._
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.effect.concurrent.Ref
import dev.profunktor.redis4cats.effect.Log
import fs2.Pipe
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import tofu.generate.GenRandom
import tofu.logging.Logging
//import tofu.syntax.logging._

import mixzpoker.domain.auth.{AuthError, AuthToken}
import mixzpoker.domain.user.{User, UserName, UserPassword}
import mixzpoker.user.UserRepository
import mixzpoker.domain.auth.AuthError._


trait AuthService[F[_]] {
  def signUp(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]]
  def signIn(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]]
  def signOut(name: UserName, token: AuthToken): F[Unit]
  def getAuthUser(token: String): F[Option[User]]

  def wsAuthPipe(userRef: Option[Ref[F, Option[User]]] = None): Pipe[F, WebSocketFrame, (Option[User], String)]
}

object AuthService {
  def create[F[_]: Sync](userRepository: UserRepository[F], repo: AuthRepository[F]): AuthService[F] =
    new AuthService[F] {

    override def signUp(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]] =
      userRepository.create(name, password).flatMap {
        case Left(err)   => (SignUpError(err): AuthError).asLeft[AuthToken].pure[F]
        case Right(user) => repo.addToken(user.name).map(_.asRight[AuthError])
      }

    override def signIn(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]] =
      userRepository.checkPassword(password, name).flatMap {
        case Some(isCorrect) if isCorrect => repo.addToken(name).map(_.asRight[AuthError])
        case Some(_)                      => (WrongPassword: AuthError).asLeft[AuthToken].pure[F]
        case None                         => (NoSuchUser: AuthError).asLeft[AuthToken].pure[F]
      }

    override def signOut(name: UserName, token: AuthToken): F[Unit] =
      repo.deleteToken(token)

    override def getAuthUser(token: String): F[Option[User]] = {
      for {
        name <- OptionT(repo.getUserName(AuthToken.fromString(token)))
        user <- OptionT(userRepository.get(name))
      } yield user
    }.value

    override def wsAuthPipe(userRef: Option[Ref[F, Option[User]]] = None): Pipe[F, WebSocketFrame, (Option[User], String)] =
      _.collect {
        case Text(text, _) => text.trim
        case Close(_)      => "disconnected"
      }.evalMapAccumulate(none[User]) {
        case (Some(user), text)  => (user.some, text).pure[F]
        case (None, token) =>
          getAuthUser(token)
            .map {
              _.fold((none[User], token)) { user =>
                (user.some, token)
              }
            }.flatTap {
              case (maybeUser, _) => userRef.traverse(_.update(_ => maybeUser))
            }
      }
  }

  def inMemory[F[_]: Sync](userRepository: UserRepository[F]): F[AuthService[F]] =
    AuthRepository.inMemory.map { repo => create(userRepository, repo) }

  def ofRedis[F[_]: Concurrent: ContextShift: GenRandom: Logging: Log](
    userRepository: UserRepository[F],
    uri: String
  ): Resource[F, AuthService[F]] = for {
    repo <- AuthRepository.ofRedis(uri)
  } yield create(userRepository, repo)
}