package mixzpoker

import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext.global
import cats.implicits._
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.effect.concurrent.Ref
import mixzpoker.auth.{AuthApi, AuthUserRepository}
import mixzpoker.user.{UserApi, UserRepository}
import mixzpoker.game.poker.{PokerApi, PokerApp}
import mixzpoker.infrastructure.broker.Broker
import mixzpoker.lobby.{LobbyApi, LobbyRepository}
import tofu.logging.Logging


object HttpServer {

  def run[F[_]: ConcurrentEffect: Timer]: F[ExitCode] = {
    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("Simple Log")

    for {
      counter <- Ref[F].of(0)
      userRepo <- UserRepository.inMemory
      authUserRepo <- AuthUserRepository.inMemory
      lobbyRepo <- LobbyRepository.inMemory
      broker <- Broker.fromQueues[F](32)
      _ <- broker.createTopic("poker-game-topic").value.map(_.fold(_.raiseError, _ => ()))
      pokerApp <- PokerApp.of(broker)

      fiber <- ConcurrentEffect[F].start(pokerApp.run)
      helloWorld = new HelloWorld(counter)
      authApi = new AuthApi(authUserRepo, userRepo)
      userApi = new UserApi(userRepo)
      pokerApi = new PokerApi(broker, pokerApp)
      lobbyApi = new LobbyApi[F](lobbyRepo, broker)

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
