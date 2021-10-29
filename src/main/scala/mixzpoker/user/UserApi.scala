package mixzpoker.user

import cats.data.EitherT
import cats.implicits._
import cats.effect.Sync
import cats.effect.Concurrent
import org.http4s.{AuthedRoutes, HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._


class UserApi[F[_]: Sync: Concurrent](userRepository: UserRepository[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case GET -> Root / "user" / name as user => getUser(name)
//    case req @ POST -> Root / "user" / "create" as user => createUser(req.req)
  }

  private def getUser(name: String): F[Response[F]] = (for {
    user <- EitherT(userRepository.getUser(UserName(name)))
    resp = Ok(UserDto.User(user.id.toString, user.name.value, user.amount).asJson)
  } yield resp).leftMap(_ => NotFound()).merge.flatten


  // todo smth for anonymous users
//  private def createUser(req: Request[F]): F[Response[F]] = for {
//    request <- req.decodeJson[UserDto.CreateUserRequest]
//    user = User.newAnonymousUser(request.name)
//    _ <- userRepository.saveUser(user)
//    resp <- Ok()
//  } yield resp

}
