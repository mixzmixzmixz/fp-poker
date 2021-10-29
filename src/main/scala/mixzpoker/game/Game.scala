package mixzpoker.game

import cats.effect.Sync
import mixzpoker.domain.Token
import mixzpoker.game.GameError.{ErrOr, WrongGameType}
import mixzpoker.game.poker.{PokerGame, PokerSettings}
import mixzpoker.user.User

trait Game[F[_]] {
  def id: GameId

  def processEvent(event: GameEvent): F[ErrOr[Unit]]
}

object Game {
  def fromUsers[F[_]: Sync](
    gameType: GameType, settings: GameSettings, users: List[(User, Token)]
  ): ErrOr[Game[F]] = gameType match {
    case GameType.Poker => settings match {
      case settings: PokerSettings => PokerGame.fromUsers(users, settings)
      case _ => Left(WrongGameType)
    }
  }
}
