package mixzpoker.game.core

case class Hand(cards: List[Card]) {
  def lastOption: Option[Card] = cards.headOption

  def addCard(card: Card): Hand = copy(cards = card::cards)
}

object Hand {
  def empty: Hand = Hand(Nil)
}
