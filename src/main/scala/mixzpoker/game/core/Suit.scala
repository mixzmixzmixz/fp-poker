package mixzpoker.game.core

import io.circe.{Decoder, Encoder}

sealed trait Suit

object Suit {
  case object Diamonds extends Suit
  case object Hearts extends Suit
  case object Clubs extends Suit
  case object Spades extends Suit

  val all: Iterable[Suit] = List(Diamonds, Hearts, Clubs, Spades)

  implicit val suitEncoder: Encoder[Suit] = Encoder[String].contramap {
    case Diamonds => "diamonds"
    case Hearts => "hearts"
    case Clubs => "clubs"
    case Spades => "spades"
  }

  implicit val suitDecoder: Decoder[Suit] = Decoder[String].emap {
    case "diamonds" => Right(Diamonds)
    case "hearts" => Right(Hearts)
    case "clubs" => Right(Clubs)
    case "spades" => Right(Spades)
    case _ => Left("wrong suit")
  }
}