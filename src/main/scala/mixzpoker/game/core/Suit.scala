package mixzpoker.game.core

sealed trait Suit

object Suit {
  case object Diamonds extends Suit
  case object Hearts extends Suit
  case object Clubs extends Suit
  case object Spades extends Suit

  val all: Iterable[Suit] = List(Diamonds, Hearts, Clubs, Spades)
}