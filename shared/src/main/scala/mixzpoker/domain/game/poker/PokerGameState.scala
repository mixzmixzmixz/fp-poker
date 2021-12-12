package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}

sealed trait PokerGameState

object PokerGameState {
  case object RoundStart extends PokerGameState
  case object PreFlop extends PokerGameState
  case object Flop extends PokerGameState
  case object Turn extends PokerGameState
  case object River extends PokerGameState

  implicit val pgsEncoder: Encoder[PokerGameState] = Encoder[String].contramap {
    case RoundStart => "RoundStart"
    case PreFlop    => "PreFlop"
    case Flop       => "Flop"
    case Turn       => "Turn"
    case River      => "River"
  }

  implicit val pgsDecoder: Decoder[PokerGameState] = Decoder[String].emap {
    case "RoundStart" => Right(RoundStart)
    case "PreFlop"    => Right(PreFlop)
    case "Flop"       => Right(Flop)
    case "Turn"       => Right(Turn)
    case "River"      => Right(River)
    case _            => Left("wrong PokerGameState")
  }
}