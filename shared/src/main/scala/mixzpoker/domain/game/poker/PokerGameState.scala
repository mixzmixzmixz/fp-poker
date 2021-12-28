package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}

sealed trait PokerGameState

object PokerGameState {
  final case object RoundStart extends PokerGameState
  final case object Flop extends PokerGameState
  final case object Turn extends PokerGameState
  final case object River extends PokerGameState
  final case object RoundEnd extends PokerGameState
  final case object GameEnd extends PokerGameState

  implicit val pgsEncoder: Encoder[PokerGameState] = Encoder[String].contramap {
    case RoundStart => "RoundStart"
    case Flop       => "Flop"
    case Turn       => "Turn"
    case River      => "River"
    case RoundEnd   => "RoundEnd"
    case GameEnd    => "RoundEnd"
  }

  implicit val pgsDecoder: Decoder[PokerGameState] = Decoder[String].emap {
    case "RoundStart" => Right(RoundStart)
    case "Flop"       => Right(Flop)
    case "Turn"       => Right(Turn)
    case "River"      => Right(River)
    case "RoundEnd"   => Right(RoundEnd)
    case "GameEnd"    => Right(GameEnd)
    case _            => Left("wrong PokerGameState")
  }
}