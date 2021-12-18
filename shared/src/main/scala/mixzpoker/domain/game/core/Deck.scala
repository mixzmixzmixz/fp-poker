package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}

import scala.util.Random

//todo random as effect
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
  final case class Deck52(cards: List[Card]) extends Deck {
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

  def of52: Deck = {
    val cards = for {
      suit <- Suit.all
      rank <- Rank.all
    } yield Card(rank, suit)

    Deck52(cards.toList)
  }

  implicit val deck52Encoder: Encoder[Deck] = Encoder[String].contramap {
    case Deck52(_) => "deck52"
  }

  implicit val deck52Decoder: Decoder[Deck] = Decoder[String].emap {
    case "deck52" => Right(of52)
    case _ => Left("unknown type of deck")
  }

}
