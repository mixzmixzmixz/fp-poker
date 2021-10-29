package mixzpoker.game.core.deck

import mixzpoker.game.core.{Card, Rank, Suit}

import scala.util.Random

//todo to set
case class Deck52(cards: List[Card]) extends Deck {
  override def getRandomCard: Option[(Card, Deck)] = cards.length match {
    case 0 => None
    case _ =>
      val cardIndex = Random.nextInt(cards.size)
      val card = cards(cardIndex)
      val cards1 = cards.take(cardIndex)
      val cards2 = cards.takeRight(cards.size - cardIndex)

      Some((card, Deck52(cards1:::cards2)))
  }
}

object Deck52 {
  def apply(): Deck52 = {
    val cards = (for {
      suit <- Suit.all
      rank <- Rank.all
    } yield Card(rank, suit)).toList
    Deck52(cards)
  }
}