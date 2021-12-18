package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}

sealed trait Suit {
  def show: String
  def isRed: Boolean
  def isBlack: Boolean = !isRed
}

object Suit {
  final case object Diamonds extends Suit {
    override def show: String = "♦"
    override def isRed: Boolean = true
  }

  final case object Hearts extends Suit {
    override def show: String = "♥"
    override def isRed: Boolean = true
  }

  final case object Clubs extends Suit {
    override def show: String = "♣"
    override def isRed: Boolean = false
  }

  final case object Spades extends Suit {
    override def show: String = "♠"
    override def isRed: Boolean = false
  }

  val all: Iterable[Suit] = List(Diamonds, Hearts, Clubs, Spades)

  implicit val suitEncoder: Encoder[Suit] = Encoder[String].contramap {
    case Diamonds => "diamonds"
    case Hearts   => "hearts"
    case Clubs    => "clubs"
    case Spades   => "spades"
  }

  implicit val suitDecoder: Decoder[Suit] = Decoder[String].emap {
    case "diamonds" => Right(Diamonds)
    case "hearts"   => Right(Hearts)
    case "clubs"    => Right(Clubs)
    case "spades"   => Right(Spades)
    case _          => Left("wrong suit")
  }
}