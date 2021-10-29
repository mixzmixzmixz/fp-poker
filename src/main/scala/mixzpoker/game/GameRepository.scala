package mixzpoker.game

import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import GameError._



trait GameRepository[F[_]] {
  def getGame(gameId: GameId): F[ErrOr[Game[F]]]

  def saveGame(game: Game[F], id: GameId): F[ErrOr[Unit]]
}


object GameRepository {
  def inMemory[F[_]: Sync]: F[GameRepository[F]] = for {
    store <- Ref.of[F, Map[GameId, Game[F]]](Map())
  } yield new GameRepository[F] {
    override def getGame(gameId: GameId): F[ErrOr[Game[F]]] = for {
      map <- store.get
      game = map.get(gameId).toRight(NoSuchGame)
    } yield game

    override def saveGame(game: Game[F], id: GameId): F[ErrOr[Unit]] = for {
      _ <- store.update { _.updated(id, game) }
    } yield Right(())
  }
}