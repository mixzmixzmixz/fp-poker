package mixzpoker

import cats.effect.{ExitCode, IO, IOApp}
import tofu.internal.carriers.DelayCarrier2.interop //todo


object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    HttpServer.run[IO]
}
