package mixzpoker.lobby

import cats.implicits._
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.effect.concurrent.Ref
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log
import tofu.generate.GenRandom
import tofu.logging.Logging
import tofu.syntax.logging._
import io.circe.syntax._
import io.circe.parser.decode

import mixzpoker.domain.game.GameType
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.domain.lobby.{Lobby, LobbyName}
import mixzpoker.domain.user.User


trait LobbyRepository[F[_]] {
  def create(name: LobbyName, owner: User, gameType: GameType): F[Option[Lobby]]
  def list(): F[List[Lobby]]
  def listWithGameStarted: F[List[Lobby]]
  def get(name: LobbyName): F[Option[Lobby]]
  def save(lobby: Lobby): F[Unit]
  def delete(name: LobbyName): F[Unit]
  def exists(name: LobbyName): F[Boolean]
}


object LobbyRepository {

  def inMemory[F[_]: Sync]: F[LobbyRepository[F]] = for {
    store  <- Ref.of[F, Map[LobbyName, Lobby]](Map.empty)
  } yield new LobbyRepository[F] {

    override def create(name: LobbyName, owner: User, gameType: GameType): F[Option[Lobby]] = {
      val settings = gameType match {
        case GameType.Poker => PokerSettings.defaults
      }
      val lobby = Lobby(name, owner, List(), gameType, settings)

      store.getAndUpdate { m =>
        if (m.contains(name)) m
        else m.updated(name, lobby)
      }.map(m => if (!m.contains(name)) lobby.some else none[Lobby])
    }

    override def list(): F[List[Lobby]] =
      store.get.map(_.values.toList)

    override def listWithGameStarted: F[List[Lobby]] =
      store.get.map(_.values.toList.filter(_.gameId.isDefined))

    override def get(name: LobbyName): F[Option[Lobby]] =
      store.get.map(_.get(name))

    override def save(lobby: Lobby): F[Unit] =
      store.update { _.updated(lobby.name, lobby) }

    override def delete(name: LobbyName): F[Unit] =
      store.update { _ - name }

    override def exists(name: LobbyName): F[Boolean] =
      get(name).map(_.isDefined)

  }

  //just a tiny replacement for SQL DB. Simple and Inefficient
  def ofRedis[F[_]: Concurrent: ContextShift: GenRandom: Logging: Log](
    uri: String = "redis://localhost:6380"
  ): Resource[F, LobbyRepository[F]] = for {
    redis <- Redis[F].utf8(uri).evalTap { redis =>
      redis.info.flatMap {
        _.get("redis_version").traverse_ { v => info"Connected to Redis $v" }
      }
    }
  } yield new LobbyRepository[F] {
    def key(name: LobbyName): String =
      s"table#lobbies#${name.toString}"

    override def create(name: LobbyName, owner: User, gameType: GameType): F[Option[Lobby]] = {
      val settings = gameType match {
        case GameType.Poker => PokerSettings.defaults
      }
      val lobby = Lobby(name, owner, List(), gameType, settings)
      redis
        .setNx(key(name), lobby.asJson.noSpaces)
        .map(set => if (set) lobby.some else none[Lobby])
    }

    override def list(): F[List[Lobby]] =
      redis
        .keys("table#lobbies#*") //todo fix, this is a terribly inefficient way to do this!
        .flatMap {
          _.map(s => LobbyName.fromString(s.drop("table#lobbies#".length)))
            .collect { case Some(name) => name }
            .traverse { get }
        }
        .map(_.collect { case Some(l) => l })

    override def listWithGameStarted: F[List[Lobby]] =
      list().map(_.filter(_.gameId.isDefined))

    override def get(name: LobbyName): F[Option[Lobby]] =
      redis
        .get(key(name))
        .map(_.map(decode[Lobby]).flatMap(_.toOption))

    override def save(lobby: Lobby): F[Unit] =
      redis.set(key(lobby.name), lobby.asJson.noSpaces)

    override def delete(name: LobbyName): F[Unit] =
      redis.del(key(name)) as ()

    override def exists(name: LobbyName): F[Boolean] =
      redis.exists(key(name))
  }
}
