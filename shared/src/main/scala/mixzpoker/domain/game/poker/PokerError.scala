package mixzpoker.domain.game.poker

import mixzpoker.domain.game.{GameError, GameId}
import mixzpoker.domain.user.UserId


sealed trait PokerError extends GameError

object PokerError {
  final case class SomeError(message: String) extends PokerError
  final case class NoSuchGame(gameId: GameId) extends PokerError
  final case class WrongDataFormat(reason: String) extends PokerError
  final case object NotEnoughPlayers extends PokerError
  final case object TooManyPlayers extends PokerError
  final case object BuyInTooLow extends PokerError
  final case object BuyInTooHigh extends PokerError
  final case object NoEmptySeat extends PokerError
  final case object NoSuchPlayer extends PokerError
  final case object UserAlreadyInGame extends PokerError
  final case object EmptyDeck extends PokerError
  final case class NoPlayerOnSeat(seat: Int) extends PokerError
  final case class NoPlayerWithUserId(userId: UserId) extends PokerError
  final case object NotEnoughTokensToCall extends PokerError
  final case object MoreTokensThanNeededToCall extends PokerError
  final case object NotEnoughTokensToRaise extends PokerError
  final case object CanNotCheck extends PokerError
  final case object UserDoesNotHaveEnoughTokens extends PokerError
  final case class UserBalanceError(message: String) extends PokerError
  final case object WrongUserId extends PokerError

}
