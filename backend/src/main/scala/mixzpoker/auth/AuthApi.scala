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
import mixzpoker.user.{User, UserRepository}
import AuthError._
import mixzpoker.domain.user.UserName


class AuthApi[F[_]: Concurrent: Logging](
  authUserRepository: AuthUserRepository[F],
  userRepository: UserRepository[F]
) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  def getAuthUser(token: String): F[Either[AuthError, User]] =
    AuthToken.fromString(token).liftTo[F].flatMap { at =>
      authUserRepository
        .getUserName(at)
        .flatMap(userRepository.get)
        .map(_.asRight[AuthError])
    }.recover { case ae: AuthError => ae.asLeft[User] }

  val authUser: Kleisli[F, Request[F], ErrOr[User]] = Kleisli({ request =>
     (for {
      header    <- request.headers.get(CaseInsensitiveString("Authorization")).toRight(NoAuthorizationHeader).liftTo[F]
      user      <- getAuthUser(header.value)
    } yield user).recover { case ae: AuthError => ae.asLeft[User] }
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
    case       GET  -> Root / "auth" / "me"       as user => Ok(user.dto.asJson)
  }

  private def signIn(request: Request[F]): F[Response[F]] = for {
    _         <- info"SignIN request"
    req       <- request.decodeJson[AuthDto.SignInRequest]
    _         <- info"SignIN request ${req.toString}"
    user      <- userRepository.get(UserName(req.userName))
    _         <- user.checkPassword(req.password).liftTo[F]
    authToken = AuthToken.fromRandom
    _         <- authUserRepository.addToken(authToken, user.name)
    resp      <- Ok("OK", Header("Authorization", authToken.toString))
  } yield resp

  private def signOut(req: Request[F], user: User): F[Response[F]] = for {
    header    <- req.headers.get(CaseInsensitiveString("Authorization")).toRight(NoAuthorizationHeader).liftTo[F]
    authToken <- AuthToken.fromString(header.value).liftTo[F]
    _         <- authUserRepository.deleteToken(authToken)
    resp      <- Ok()
  } yield resp

  private def signUp(request: Request[F]): F[Response[F]] = for {
    req       <- request.decodeJson[AuthDto.RegisterUserRequest]
    user      <- User.create(req.userName, req.password).liftTo[F]
    _         <- userRepository.checkUserAlreadyExist(user.name)
    _         <- userRepository.save(user)
    authToken = AuthToken.fromRandom
    _         <- authUserRepository.addToken(authToken, user.name)
    resp      <- Ok("OK", Header("Authorization", authToken.toString))
  } yield resp
}

