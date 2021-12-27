package mixzpoker

import cats.implicits._
import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, Timer, Resource}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}
import tofu.logging.Logging
import tofu.Delay
import tofu.generate.GenRandom

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

import mixzpoker.auth.{AuthApi, AuthService}
import mixzpoker.user.{UserApi, UserRepository}
import mixzpoker.game.poker.{PokerApi, PokerService}
import mixzpoker.lobby.{LobbyApi, LobbyRepository, LobbyService}



object HttpServer {
  def make[F[_]: ConcurrentEffect: Timer: Delay: Logging: GenRandom](
    userRepo: UserRepository[F],
    lobbyRepo: LobbyRepository[F],
    authService: AuthService[F],
    lobbyService: LobbyService[F],
    pokerService: PokerService[F]
  ): Resource[F, F[Unit]] = {
    val authApi    = new AuthApi[F](authService)
    val userApi    = new UserApi[F](userRepo)
    val pokerApi   = new PokerApi[F](pokerService, lobbyRepo, authService)
    val lobbyApi   = new LobbyApi[F](lobbyService, lobbyRepo, authService)

    val services =
      authApi.routes <+>
        lobbyApi.routes <+>
        pokerApi.routes <+>
        authApi.middleware(
          userApi.authedRoutes <+>
            pokerApi.authedRoutes <+>
            authApi.authedRoutes <+>
            lobbyApi.authedRoutes
        )

    val httpApp = Router("/api" -> CORS(services,
      config = CORSConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set("GET", "POST")),
        allowCredentials = true,
        maxAge = 1.day.toSeconds
      )
    )).orNotFound

    BlazeServerBuilder[F](global)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .background

  }
}
