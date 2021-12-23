package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}

import scala.util.Random


trait Deck {
  def getFirstNCards(n: Int = 1): Option[(List[Card], Deck)]
  def size: Int
}

object Deck {
  final case class Deck52(cards: List[Card]) extends Deck {
    override def getFirstNCards(n: Int): Option[(List[Card], Deck)] =
      if (n > cards.size) None
      else
        Some((cards.take(n), copy(cards = cards.drop(n))))

    override def size: Int = cards.size
  }

  def of52: Deck = Deck52(cards52)

  def ofCards52(cards: List[Card]): Deck = Deck52(cards)

  def cards52: List[Card] = {
    for {
      suit <- Suit.all
      rank <- Rank.all
    } yield Card(rank, suit)
  }.toList

  def shuffledOf52(seed: Long): Deck =
    Deck52(new Random(seed).shuffle(cards52))

  implicit val deck52Encoder: Encoder[Deck] = Encoder[String].contramap {
    case Deck52(_) => "deck52"
  }

  implicit val deck52Decoder: Decoder[Deck] = Decoder[String].emap {
    case "deck52" => Right(of52)
    case _ => Left("Unknown Deck type")
  }

}
