package mixzpoker.user

import cats.implicits._
import cats.effect.Sync
import cats.effect.Concurrent
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._
import mixzpoker.domain.user.{User, UserName, UserRequest}


class UserApi[F[_]: Sync: Concurrent](userRepository: UserRepository[F]) {
  val dsl = new Http4sDsl[F]{}
  import dsl._

  def authedRoutes: AuthedRoutes[User, F] = AuthedRoutes.of {
    case       GET  -> Root / "user" / name as user => getUser(name)
    case req @ POST -> Root / "user" / "gimme_money" as user => giveUseMoney(user, req.req)
//    case req @ POST -> Root / "user" / "create" as user => createUser(req.req)
  }

  private def getUser(name: String): F[Response[F]] = for {
    user <- userRepository.get(UserName(name))
    resp <- Ok(user.asJson)
  } yield resp

  private def giveUseMoney(user: User, req: Request[F]): F[Response[F]] = {
    req.decodeJson[UserRequest.GimmeMoneyRequest].flatMap { request =>
      userRepository.changeMoney(user, request.money)
    }.ifM(Ok(), NotFound())
  }

  // todo smth for anonymous players
//  private def createUser(req: Request[F]): F[Response[F]] = for {
//    request <- req.decodeJson[UserDto.CreateUserRequest]
//    user = user.newAnonymousUser(request.name)
//    _ <- userRepository.save(user)
//    resp <- Ok()
//  } yield resp

}
