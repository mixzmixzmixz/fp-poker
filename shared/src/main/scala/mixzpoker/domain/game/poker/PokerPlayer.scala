package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import mixzpoker.domain.Token
import mixzpoker.domain.user.{UserId, UserName}
import mixzpoker.domain.game.core.Hand
import mixzpoker.domain.game.poker.PokerPlayerState._
import mixzpoker.domain.game.poker.PokerError._


final case class PokerPlayer(
  userId: UserId,
  name: UserName,
  seat: Int,
  tokens: Token,
  hand: Hand,
  state: PokerPlayerState
) {
  def hasCards: Boolean = hand.nonEmpty

  def isAllIned: Boolean = state match {
    case AllIned => true
    case _       => false
  }

  def fold(): PokerPlayer = copy(hand = Hand.empty, state = Folded)
  def check(): PokerPlayer = copy(state = Checked)
  def call(): PokerPlayer = copy(state = Called)
  def raise(): PokerPlayer = copy(state = Raised)
  def allIn(): PokerPlayer = copy(state = AllIned)

  def decreaseBalance(delta: Token): Either[PokerError, PokerPlayer] =
    Either.cond(delta <= tokens, copy(tokens = tokens - delta), UserDoesNotHaveEnoughTokens)

  def increaseBalance(delta: Token): PokerPlayer =
    copy(tokens = tokens + delta)
}


object PokerPlayer {
  def fromUser(userId: UserId,name: UserName, buyIn: Token, seat: Int): PokerPlayer =
    PokerPlayer(
      userId = userId,
      name = name,
      seat = seat,
      tokens = buyIn,
      hand = Hand.empty,
      state = PokerPlayerState.Folded
    )


  implicit val pokerPlayerEncoder: Encoder[PokerPlayer] = deriveEncoder
  implicit val pokerPlayerDecoder: Decoder[PokerPlayer] = deriveDecoder
}
