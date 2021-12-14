package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}


sealed trait PokerPlayerState

object PokerPlayerState {
  case object Joined extends PokerPlayerState
  case object Folded extends PokerPlayerState
  case object Checked extends PokerPlayerState
  case object Called extends PokerPlayerState
  case object AllIned extends PokerPlayerState
  case object Raised extends PokerPlayerState
  case object Active extends PokerPlayerState

  implicit val pokerPlayerStateEncoder: Encoder[PokerPlayerState] = Encoder[String].contramap {
    case Joined  => "joined"
    case Folded  => "folded"
    case Checked => "checked"
    case Called  => "called"
    case AllIned => "allined"
    case Raised  => "raised"
    case Active  => "active"
  }

  implicit val pokerPlayerStateDecoder: Decoder[PokerPlayerState] = Decoder[String].emap {
    case "joined"  => Right(Joined)
    case "folded"  => Right(Folded)
    case "checked" => Right(Checked)
    case "called"  => Right(Called)
    case "allined" => Right(AllIned)
    case "raised"  => Right(Raised)
    case "active"  => Right(Active)
    case _         => Left("wrong player state")
  }
}