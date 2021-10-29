package mixzpoker.game.poker.game

sealed trait PokerGameError

object PokerGameError {
  type ErrOr[A] = Either[PokerGameError, A]

  case object NotEnoughTokensToCall extends PokerGameError
  case object MoreTokensThanNeededToCall extends PokerGameError
  case object NotEnoughTokensToRaise extends PokerGameError
  case object CanNotCheck extends PokerGameError
  case object UserDoesNotHaveEnoughTokens extends PokerGameError
  case class UserBalanceError(message: String) extends PokerGameError
}