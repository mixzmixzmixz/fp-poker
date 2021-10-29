package mixzpoker.game.poker.player

import mixzpoker.game.core.{Card, Hand}


sealed trait PokerPlayerState {
  def player: PokerPlayer
}

object PokerPlayerState {
  case class JoinedPlayer(player: PokerPlayer) extends PokerPlayerState

  case class FoldedPlayer(player: PokerPlayer) extends PokerPlayerState

  case class ActivePlayer(player: PokerPlayer, hand: Hand) extends PokerPlayerState {
    def giveCard(card: Card): PokerPlayerState = copy(hand = hand.addCard(card))
  }


  def fromPlayer(player: PokerPlayer): Option[PokerPlayerState] = ???
}