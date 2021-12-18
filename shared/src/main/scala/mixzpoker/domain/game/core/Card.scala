package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


final case class Card(rank: Rank, suit: Suit) {
  def show: String = s"${rank.show}${suit.show}"
  def isRed: Boolean = suit.isRed
  def isBlack: Boolean = suit.isBlack
}

object Card {

  implicit val cardEncoder: Encoder[Card] = deriveEncoder
  implicit val cardDecoder: Decoder[Card] = deriveDecoder
}
