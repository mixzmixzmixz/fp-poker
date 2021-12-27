package mixzpoker.user

import cats.implicits._
import cats.effect.IO
import dev.profunktor.redis4cats.effect.Log.Stdout._
import mixzpoker.domain.user.{UserName, UserPassword}
import org.scalatest.flatspec.AnyFlatSpec
import tofu.generate.GenRandom
import tofu.logging.Logging

import scala.concurrent.ExecutionContext.global


class UserRepositorySpec extends AnyFlatSpec {

  implicit val makeLogging: Logging.Make[IO] = Logging.Make.plain[IO]
  implicit val logging: Logging[IO] = makeLogging.byName("TestLog")

  "User creation and extraction" should "work" in {
    implicit val genRandom = GenRandom.instance[IO, IO]().unsafeRunSync()
    implicit val cs = IO.contextShift(global)

    val repo = UserRepository.ofRedis[IO]()
    val name = UserName.fromString("TestUserName").get
    val password = UserPassword.fromString("testpw")

    val createdUser =  repo.use { ur =>
      ur.create(name, password)
    }.unsafeRunSync()

    assert(createdUser.isRight)

    val extractedUser = repo.use { ur =>
      ur.get(name)
    }.unsafeRunSync()

    assert(extractedUser.isDefined)

    assert(createdUser.toOption.get == extractedUser.get)

  }


}
