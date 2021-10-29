package mixzpoker.game.poker

import mixzpoker.game.{GameError, GameId}


sealed trait PokerError extends GameError

object PokerError {
  type ErrOr[A] = Either[PokerError, A]

  case class SomeError(message: String) extends PokerError
  case class NoSuchGame(gameId: GameId) extends PokerError
  case class WrongDataFormat(reason: String) extends PokerError

}
