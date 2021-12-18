package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


final case class Hand(cards: List[Card]) {
  def lastOption: Option[Card] = cards.headOption

  def addCard(card: Card): Hand = copy(cards = card::cards)
  def addCards(cs: List[Card]): Hand = copy(cards = cs:::cards)

  def isEmpty: Boolean = cards.isEmpty
  def nonEmpty: Boolean = cards.nonEmpty
}

object Hand {
  def empty: Hand = Hand(Nil)

  implicit val handEncoder: Encoder[Hand] = deriveEncoder
  implicit val handDecoder: Decoder[Hand] = deriveDecoder
}
