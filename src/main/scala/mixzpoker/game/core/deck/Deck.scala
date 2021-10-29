package mixzpoker.game.core.deck

import mixzpoker.game.core.Card

trait Deck {
  def getRandomCard: Option[(Card, Deck)]

  def getRandomCards(count: Int): Option[(List[Card], Deck)] = count match {
    case 0 => Some((List(), this))
    case _ => for {
      (card, newDeck) <- getRandomCard
      (cards, newDeck2) <- newDeck.getRandomCards(count - 1)
    } yield (card :: cards, newDeck2)
  }
}

object Deck {
  def of52: Deck = Deck52()
}
