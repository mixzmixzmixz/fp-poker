package mixzpoker

import cats.implicits._
import cats.effect.syntax.all._
import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp, Timer}
import tofu.Delay
import tofu.generate.GenRandom
//import tofu.internal.carriers.DelayCarrier2.interop
import tofu.logging.Logging

import mixzpoker.auth.AuthService
import mixzpoker.game.poker.PokerService
import mixzpoker.lobby.{LobbyRepository, LobbyService}
import mixzpoker.user.UserRepository


object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
//    HttpServer.runBackground[IO]
    make[IO]

  def make[F[_]: ConcurrentEffect: Timer: Delay]: F[ExitCode] = {
    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("MainLog")


    val server = GenRandom.instance[F, F]().flatMap { implicit genRandom =>
      for {
        userRepo     <- UserRepository.inMemory
        lobbyRepo    <- LobbyRepository.inMemory
        authService  <- AuthService.inMemory(userRepo)
        pokerSrvRes  <- PokerService.create
        lobbySrvRes  =  pokerSrvRes.evalMap(ps => LobbyService.create(lobbyRepo, ps)).flatten
      } yield
        for {
          pokerService <- pokerSrvRes
          lobbyService <- lobbySrvRes
          server       <- HttpServer.make(userRepo, lobbyRepo, authService, lobbyService, pokerService)
        } yield server
    }

    server.flatMap { srv => srv.use( _ => ConcurrentEffect[F].never[Unit] as ExitCode.Success)}
  }
}
