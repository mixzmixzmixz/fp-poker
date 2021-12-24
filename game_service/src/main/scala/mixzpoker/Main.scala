package mixzpoker

import cats.Parallel
import cats.effect.{Clock, Concurrent, ContextShift, ExitCode, IO, IOApp, Timer}
import com.evolutiongaming.catshelper.{Blocking, FromFuture}
import com.evolutiongaming.smetrics.MeasureDuration

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    implicit val executor: ExecutionContextExecutor = ExecutionContext.global

    implicit val concurrentIO: Concurrent[IO]         = IO.ioConcurrentEffect
    implicit val parallelIO: Parallel[IO]             = IO.ioParallel
    implicit val fromFutureIO: FromFuture[IO]         = FromFuture.lift[IO]
    implicit val measureDuration: MeasureDuration[IO] = MeasureDuration.fromClock[IO](Clock[IO])
    implicit val blocking: Blocking[IO]               = Blocking.empty[IO]

    GameServer.run[IO]
  }
}