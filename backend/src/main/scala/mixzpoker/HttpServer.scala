package mixzpoker

import cats.implicits._
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}
import tofu.logging.Logging
import tofu.Delay
import tofu.generate.GenRandom

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import mixzpoker.auth.{AuthApi, AuthService}
import mixzpoker.user.{UserApi, UserRepository}
import mixzpoker.game.poker.{PokerApi, PokerService}
import mixzpoker.lobby.{LobbyApi, LobbyRepository, LobbyService}



object HttpServer {

  def run[F[_]: ConcurrentEffect: Timer: Delay]: F[ExitCode] = {
    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("MainLog")

    GenRandom.instance[F, F]().flatMap { implicit genRandom =>
      for {
        userRepo     <- UserRepository.inMemory
        lobbyRepo    <- LobbyRepository.inMemory
        authService  <- AuthService.inMemory(userRepo)
        pokerService <- PokerService.of
        lobbyService <- LobbyService.of(lobbyRepo, pokerService)

        fiber1 <- ConcurrentEffect[F].start(pokerService.run)
        fiber2 <- ConcurrentEffect[F].start(lobbyService.run)

        authApi    = new AuthApi[F](authService)
        userApi    = new UserApi[F](userRepo)
        pokerApi   = new PokerApi[F](pokerService, lobbyRepo, authService)
        lobbyApi   = new LobbyApi[F](lobbyService, lobbyRepo, authService)

        services =
          authApi.routes <+>
            lobbyApi.routes <+>
            pokerApi.routes <+>
            authApi.middleware(
              userApi.authedRoutes <+>
                pokerApi.authedRoutes <+>
                authApi.authedRoutes <+>
                lobbyApi.authedRoutes
            )

        httpApp = Router("/api" -> CORS(services,
          config = CORSConfig(
            anyOrigin = true,
            anyMethod = false,
            allowedMethods = Some(Set("GET", "POST")),
            allowCredentials = true,
            maxAge = 1.day.toSeconds
          )
        )).orNotFound

        _ <- BlazeServerBuilder[F](global)
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(httpApp)
          .serve
          .compile
          .drain

      } yield ExitCode.Success
    }
  }
}
