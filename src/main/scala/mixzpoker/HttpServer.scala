package mixzpoker

import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext.global
import cats.implicits._
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.effect.concurrent.Ref
import mixzpoker.auth.{AuthApi, AuthUserRepository}
import mixzpoker.user.{UserApi, UserRepository}
import mixzpoker.game.poker.PokerApi
import mixzpoker.game.{GameEvent, GameRepository}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.lobby.{LobbyApi, LobbyRepository}


object HttpServer {

  def run[F[_]: ConcurrentEffect: Timer]: F[ExitCode] = {
    for {
      counter <- Ref[F].of(0)
      userRepo <- UserRepository.inMemory
      pokerGameRepo <- GameRepository.inMemory
      authUserRepo <- AuthUserRepository.inMemory
      lobbyRepo <- LobbyRepository.inMemory
      broker <- Broker.fromQueues[F, GameEvent, String](32)

      helloWorld = new HelloWorld(counter)
      userApi = new UserApi(userRepo)
      pokerApi = new PokerApi(pokerGameRepo)
      authApi = new AuthApi(authUserRepo, userRepo)
      lobbyApi = new LobbyApi[F](lobbyRepo, pokerGameRepo)

      httpApp = (
        authApi.routes <+>
        helloWorld.routes <+>
        authApi.middleware(userApi.authedRoutes) <+>
        authApi.middleware(pokerApi.authedRoutes) <+>
        authApi.middleware(authApi.authedRoutes) <+>
        authApi.middleware(lobbyApi.authedRoutes)
      ).orNotFound

    } yield for {
      _ <- BlazeServerBuilder[F](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
        .compile
        .drain
    } yield ExitCode.Success
  }.flatten
}
