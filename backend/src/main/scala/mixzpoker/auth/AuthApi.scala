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
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.domain.user.User
import mixzpoker.domain.auth.{AuthRequest, AuthToken}


class AuthApi[F[_]: Concurrent: Logging: GenUUID](authService: AuthService[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  val authUser: Kleisli[F, Request[F], Either[String, User]] = Kleisli({ request =>
    request
      .headers
      .get(CaseInsensitiveString("Authorization"))
      .fold( "no auth token".asLeft[User].pure[F] ) { h =>
        authService.getAuthUser(h.value).map(_.toRight("no such user"))
      }
  })

  val onFailure: AuthedRoutes[String, F] = Kleisli(_ => OptionT.liftF(Forbidden()))

  val middleware: AuthMiddleware[F, User] = AuthMiddleware(authUser, onFailure)

  def service: HttpRoutes[F] = middleware(authedRoutes) <+> routes

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "auth" / "sign-in" => signIn(req)
    case req @ POST -> Root / "auth" / "sign-up" => signUp(req)
    case req @ GET  -> Root / "hello" / name     => Ok(s"Hello, $name!")
  }

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case req @ POST -> Root / "auth" / "sign-out" as user => signOut(req.req, user)
    case       GET  -> Root / "auth" / "me"       as user => Ok(user.asJson)
  }

  private def signIn(request: Request[F]): F[Response[F]] =
    request.decodeJson[AuthRequest.SignInRequest].flatMap { req =>
      authService.signIn(req.userName, req.password).flatMap {
        _.fold(
          err => warn"SignIn: ${err.toString}" *> Forbidden(s"reason: ${err.toString}"), //todo structured error message
          token => Ok("OK", Header("Authorization", token.toString))
        )
      }
    }

  private def signOut(req: Request[F], user: User): F[Response[F]] =
    req.headers.get(CaseInsensitiveString("Authorization")).fold(Ok()) { header =>
      authService.signOut(user.name, AuthToken.fromString(header.value)) *> Ok()
    }

  private def signUp(request: Request[F]): F[Response[F]] =
    request.decodeJson[AuthRequest.RegisterUserRequest].flatMap { req =>
      authService.signUp(req.userName, req.password).flatMap {
        _.fold(
          err => warn"SignUp: ${err.toString}" *> Forbidden(s"reason: ${err.toString}"), //todo structured error message
          token => Ok("OK", Header("Authorization", token.toString))
        )
      }
    }
}

