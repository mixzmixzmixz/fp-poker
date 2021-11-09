package mixzpoker.game.poker

import mixzpoker.game.{GameError, GameId}
import mixzpoker.user.UserId


sealed trait PokerError extends GameError

object PokerError {
  type ErrOr[A] = Either[PokerError, A]

  case class SomeError(message: String) extends PokerError
  case class NoSuchGame(gameId: GameId) extends PokerError
  case class WrongDataFormat(reason: String) extends PokerError
  case object NotEnoughPlayers extends PokerError
  case object TooManyPlayers extends PokerError
  case class NoPlayerOnSeat(seat: Int) extends PokerError
  case class NoPlayerWithUserId(userId: UserId) extends PokerError
  case object NotEnoughTokensToCall extends PokerError
  case object MoreTokensThanNeededToCall extends PokerError
  case object NotEnoughTokensToRaise extends PokerError
  case object CanNotCheck extends PokerError
  case object UserDoesNotHaveEnoughTokens extends PokerError
  case class UserBalanceError(message: String) extends PokerError
  case object WrongUserId extends PokerError

}
