package mixzpoker

import cats.implicits._
import cats.effect.{Clock, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import com.evolutiongaming.catshelper.{FromTry, ToFuture, ToTry}
import com.evolutiongaming.smetrics.MeasureDuration
import tofu.Delay
import tofu.generate.GenRandom
//import tofu.internal.carriers.DelayCarrier2.interop
import tofu.logging.Logging
import dev.profunktor.redis4cats.effect.Log.Stdout._

import mixzpoker.auth.AuthService
import mixzpoker.game.poker.PokerService
import mixzpoker.lobby.{LobbyRepository, LobbyService}
import mixzpoker.user.UserRepository


object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    implicit val measureDuration: MeasureDuration[IO] = MeasureDuration.fromClock[IO](Clock[IO])
//    makeInMemory[IO]
    makeLive[IO]
  }


  def makeInMemory[
    F[_]: ConcurrentEffect: Timer: Delay: ContextShift: ToTry: ToFuture: FromTry: MeasureDuration
  ]: F[ExitCode] = {
    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("MainLog")


    val server = GenRandom.instance[F, F]().flatMap { implicit genRandom =>
      for {
        userRepo     <- UserRepository.inMemory
        lobbyRepo    <- LobbyRepository.inMemory
        authService  <- AuthService.inMemory(userRepo)
        pokerSrvRes  <- PokerService.of
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

  def makeLive[
    F[_]: ConcurrentEffect: Timer: Delay: ContextShift: ToTry: ToFuture: FromTry: MeasureDuration
  ]: F[ExitCode] = {
    implicit val makeLogging: Logging.Make[F] = Logging.Make.plain[F]
    implicit val logging: Logging[F] = makeLogging.byName("MainLog")


    val server = GenRandom.instance[F, F]().flatMap { implicit genRandom =>
      for {
        pokerSrvRes  <- PokerService.of
      } yield
        for {
          lobbyRepo    <- LobbyRepository.ofRedis(Config.REDIS_URI)
          userRepo     <- UserRepository.ofRedis(Config.REDIS_URI)
          authService  <- AuthService.ofRedis(userRepo, Config.REDIS_URI)
          pokerService <- pokerSrvRes
          lobbyService <- pokerSrvRes.evalMap(ps => LobbyService.create(lobbyRepo, ps)).flatten
          server       <- HttpServer.make(userRepo, lobbyRepo, authService, lobbyService, pokerService)
        } yield server
    }

    server.flatMap { srv => srv.use( _ => ConcurrentEffect[F].never[Unit] as ExitCode.Success)}
  }
}
