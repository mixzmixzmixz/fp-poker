package mixzpoker


import cats.implicits._
import cats.effect.Sync

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import io.circe.syntax._
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import io.circe.generic.JsonCodec



class HelloWorld[F[_]: Sync : Concurrent](counter: Ref[F, Int]) {
  object Response {

    @JsonCodec
    case class HelloWorld(test: String, hello: String)
  }

  val dsl = new Http4sDsl[F]{}
  import dsl._

  def routes: HttpRoutes[F] = {
    HttpRoutes.of[F] {
      case GET -> Root / "hello" / name =>
        for {
          cnt <- counter.getAndUpdate(_ + 1)
          resp <- Ok(Response.HelloWorld(s"Hello there ($cnt) ", name).asJson)
        } yield resp

    }
  }
}