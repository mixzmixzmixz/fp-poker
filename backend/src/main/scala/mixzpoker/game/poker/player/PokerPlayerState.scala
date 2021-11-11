package mixzpoker.game.poker.player

import io.circe.{Decoder, Encoder}


sealed trait PokerPlayerState

object PokerPlayerState {
  case object Joined extends PokerPlayerState
  case object Folded extends PokerPlayerState
  case object Active extends PokerPlayerState

  implicit val pokerPlayerStateEncoder: Encoder[PokerPlayerState] = Encoder[String].contramap {
    case Joined => "joined"
    case Folded => "folded"
    case Active => "active"
  }

  implicit val pokerPlayerStateDecoder: Decoder[PokerPlayerState] = Decoder[String].emap {
    case "joined" => Right(Joined)
    case "folded" => Right(Folded)
    case "active" => Right(Active)
    case _ => Left("wrong player state")
  }
}