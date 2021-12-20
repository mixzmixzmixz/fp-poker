package mixzpoker.auth

import cats.data.OptionT
import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import mixzpoker.domain.auth.{AuthError, AuthToken}
import mixzpoker.domain.user.{User, UserName, UserPassword}
import mixzpoker.user.UserRepository
import mixzpoker.domain.auth.AuthError._
import tofu.generate.GenUUID

trait AuthService[F[_]] {
  def signUp(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]]
  def signIn(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]]
  def signOut(name: UserName, token: AuthToken): F[Unit]
  def getAuthUser(token: String): F[Option[User]]
}

object AuthService {
  def inMemory[F[_]: Sync](userRepository: UserRepository[F]): F[AuthService[F]] = for {
    store <- Ref.of[F, Map[AuthToken, UserName]](Map.empty)
  } yield new AuthService[F] {

    private def getUserName(authToken: AuthToken): F[Option[UserName]] =
      store.get.map(_.get(authToken))

    private def addToken(name: UserName): F[AuthToken] = for {
      token <- GenUUID[F].randomUUID.map(AuthToken.fromUUID)
      _     <- store.update(_.updated(token, name))
    } yield token

    private def deleteToken(authToken: AuthToken): F[Unit] =
      store.update { _ - authToken }

    override def signUp(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]] =
      userRepository.create(name, password).flatMap {
        case Left(err)   => (SignUpError(err): AuthError).asLeft[AuthToken].pure[F]
        case Right(user) => addToken(user.name).map(_.asRight[AuthError])
      }

    override def signIn(name: UserName, password: UserPassword): F[Either[AuthError, AuthToken]] =
      userRepository.checkPassword(password, name).flatMap {
        case Some(isCorrect) if isCorrect => addToken(name).map(_.asRight[AuthError])
        case Some(_)                      => (WrongPassword: AuthError).asLeft[AuthToken].pure[F]
        case None                         => (NoSuchUser: AuthError).asLeft[AuthToken].pure[F]
      }

    override def signOut(name: UserName, token: AuthToken): F[Unit] =
      deleteToken(token)

    override def getAuthUser(token: String): F[Option[User]] = {
      for {
        name <- OptionT(getUserName(AuthToken.fromString(token)))
        user <- OptionT(userRepository.get(name))
      } yield user
    }.value
  }
}