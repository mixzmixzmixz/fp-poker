package mixzpoker.domain.game

import io.circe.{Decoder, Encoder}


sealed trait GameType

object GameType {
  case object Poker extends GameType

  def all: List[GameType] = List(Poker)

  implicit val decodeGameType: Decoder[GameType] = Decoder[String].emap {
    case "poker" => Right(Poker)
    case other   => Left(s"Invalid mode: $other")
  }

  implicit val encodeGameType: Encoder[GameType] = Encoder[String].contramap {
    case Poker => "poker"
  }
}
