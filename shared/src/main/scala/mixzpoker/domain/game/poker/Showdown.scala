package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Showdown(combs: List[List[(PokerCombination, PokerPlayer)]])

object Showdown {
  implicit val showdownEncoder: Encoder[Showdown] = deriveEncoder
  implicit val showdownDecoder: Decoder[Showdown] = deriveDecoder
}
