package mixzpoker.lobby

import cats.implicits._
import cats.effect.IO
import dev.profunktor.redis4cats.effect.Log.Stdout._
import mixzpoker.domain.game.GameType
import mixzpoker.domain.lobby.LobbyName
import mixzpoker.domain.user.User
import org.scalatest.flatspec.AnyFlatSpec
import tofu.generate.GenRandom
import tofu.logging.Logging

import scala.concurrent.ExecutionContext.global

class LobbyRepositorySpec extends AnyFlatSpec {
  implicit val makeLogging: Logging.Make[IO] = Logging.Make.plain[IO]
  implicit val logging: Logging[IO] = makeLogging.byName("TestLog")

  implicit val genRandom = GenRandom.instance[IO, IO]().unsafeRunSync()
  implicit val cs = IO.contextShift(global)
  val repo = LobbyRepository.ofRedis[IO]()

  "Lobby list" should "return smth" in {

    val name = LobbyName.fromString("TestLobbyNameList1").get
    val user = User.empty
    repo.use(_.create(name, user, GameType.Poker)).unsafeRunSync()

    val list = repo.use(_.list()).unsafeRunSync()

    println(list)
    assert(list.nonEmpty)
    assert(list.exists(_.name == name))

    val listWithGame = repo.use(_.listWithGameStarted).unsafeRunSync()
    assert(!listWithGame.exists(_.name == name))

    repo.use(_.delete(name)).unsafeRunSync()
  }

  "Lobby creation and extraction" should "work" in {
    val name = LobbyName.fromString("TestCreateAndDeleteLobbyName").get
    val user = User.empty
    val lobbyCreatedMb = repo.use(_.create(name, user, GameType.Poker)).unsafeRunSync()
    assert(lobbyCreatedMb.isDefined)
    println(lobbyCreatedMb)

    val lobbyGottenMb = repo.use(_.get(name)).unsafeRunSync()
    assert(lobbyGottenMb.isDefined)
    println(lobbyGottenMb)

    val lobbyCreated = lobbyCreatedMb.get
    val lobbyGotten  = lobbyGottenMb.get

    assert(lobbyCreated == lobbyGotten)

    repo.use(_.delete(name)).unsafeRunSync()
    val lobbyGottenAfterDel = repo.use(_.get(name)).unsafeRunSync()
    println(lobbyGottenAfterDel)
    assert(lobbyGottenAfterDel.isEmpty)
  }
}
