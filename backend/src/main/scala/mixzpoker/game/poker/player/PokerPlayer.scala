package mixzpoker.game.poker.player

import io.circe.generic.JsonCodec
import mixzpoker.domain.Token
import mixzpoker.game.core.Hand
import mixzpoker.game.poker.PokerError._
import mixzpoker.user.UserId


@JsonCodec
case class PokerPlayer(userId: UserId, seat: Int, tokens: Token, hand: Hand, state: PokerPlayerState) {
  def decreaseBalance(delta: Token): ErrOr[PokerPlayer] =
    Either.cond(delta <= tokens, copy(tokens = tokens - delta), UserDoesNotHaveEnoughTokens)

  def increaseBalance(delta: Token): ErrOr[PokerPlayer] =
    Right(copy(tokens = tokens + delta))

  def checkBalanceEquals(amount: Token): ErrOr[Unit] =
    Either.cond(tokens == amount, (), UserBalanceError(s"balance not equal $amount"))

  def fold(): PokerPlayer =
    copy(hand = Hand.empty, state = PokerPlayerState.Folded)

  def call(amount: Token): ErrOr[PokerPlayer] = for {
    p <- decreaseBalance(amount)
  } yield p

  def raise(amount: Token): ErrOr[PokerPlayer] = for {
    p <- decreaseBalance(amount)
  } yield p

  def allIn(amount: Token): ErrOr[PokerPlayer] = for {
    p <- decreaseBalance(amount)
  } yield p
}


object PokerPlayer {
  def fromUser(userId: UserId, buyIn: Token, seat: Int): PokerPlayer =
    PokerPlayer(
      userId = userId,
      seat = seat,
      tokens = buyIn,
      hand = Hand.empty,
      state = PokerPlayerState.Joined
    )

}
