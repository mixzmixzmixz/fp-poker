package mixzpoker.auth

import cats.effect._
import cats.implicits._
import cats.data._
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.util.CaseInsensitiveString
import io.circe.syntax._
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.user.{User, UserName, UserRepository}
import mixzpoker.domain.User.UserDto.UserDto
import AuthError._


class AuthApi[F[_]: Sync: Concurrent: Logging](authUserRepository: AuthUserRepository[F], userRepository: UserRepository[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  val authUser: Kleisli[F, Request[F], ErrOr[User]] = Kleisli({ request =>
    val token = for {
      header <- request.headers.get(CaseInsensitiveString("Authorization")).toRight(NoAuthorizationHeader)
      authToken <- AuthToken.fromString(header.value)
    } yield authToken
    (for {
      authToken <- EitherT(token.pure[F])
      userName <- EitherT(authUserRepository.getUserName(authToken))
      user <- EitherT(userRepository.getUser(userName)).leftMap[AuthError](UserErrorWrapper)
    } yield user).value
  })

  val onFailure: AuthedRoutes[AuthError, F] = Kleisli(req => OptionT.liftF(Forbidden()))

  val middleware: AuthMiddleware[F, User] = AuthMiddleware(authUser, onFailure)

  def service: HttpRoutes[F] = middleware(authedRoutes) <+> routes

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "auth" / "sign-in" => signIn(req)

    case req @ POST -> Root / "auth" / "sign-up" => signUp(req)

  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case req @ POST -> Root / "auth" / "sign-out" as user => signOut(req.req, user)

    case GET -> Root / "auth" / "me" as user =>
      Ok(UserDto(user.id.toString, user.name.value, user.amount).asJson)
  }

  private def signIn(req: Request[F]): F[Response[F]] = {
    for {
      _ <- EitherT.right(info"SignIN request")
      request <- EitherT.right(req.decodeJson[AuthDto.SignInRequest])
      _ <- EitherT.right(info"SignIN request ${request.toString}")
      user <- EitherT(userRepository.getUser(UserName(request.userName))).leftMap[AuthError](UserErrorWrapper)
      _ <- EitherT.fromEither[F](user.checkPassword(request.password)).leftMap[AuthError](UserErrorWrapper)
      authToken = AuthToken.fromRandom
      _ <- EitherT(authUserRepository.addToken(authToken, user.name))
    } yield Ok("OK", Header("Authorization", authToken.toString))
  }.leftMap { err => Forbidden(err.toString) }.merge.flatten

  private def signOut(req: Request[F], user: User): F[Response[F]] = {
    val token = for {
      header <- req.headers.get(CaseInsensitiveString("Authorization")).toRight(NoAuthorizationHeader)
      authToken <- AuthToken.fromString(header.value)
    } yield authToken
    (for {
      authToken <- EitherT(token.pure[F])
      _ <- EitherT(authUserRepository.deleteToken(authToken))
      resp = Ok()
    } yield resp).leftMap { err => Forbidden(err.toString) }.merge.flatten
  }

  private def signUp(req: Request[F]): F[Response[F]] = {
    val userET = for {
      request <- EitherT.right(req.decodeJson[AuthDto.RegisterUserRequest])
      user <- EitherT(User.create(request.userName, request.password).pure[F])
      _ <- EitherT(userRepository.checkUserAlreadyExist(user.name))
      _ <- EitherT(userRepository.saveUser(user))
    } yield user
    for {
      user <- userET.leftMap[AuthError](UserErrorWrapper)
      authToken = AuthToken.fromRandom
      _ <- EitherT(authUserRepository.addToken(authToken, user.name))
      resp = Ok("OK", Header("Authorization", authToken.toString))
    } yield resp
  }.leftMap { err => Forbidden(err.toString) }.merge.flatten
}

