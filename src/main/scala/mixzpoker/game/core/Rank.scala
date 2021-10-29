package mixzpoker.game.core

sealed trait Rank

object Rank {
  case object Ace extends Rank
  case object King extends Rank
  case object Queen extends Rank
  case object Jack extends Rank
  case object Ten extends Rank
  case object Nine extends Rank
  case object Eight extends Rank
  case object Seven extends Rank
  case object Six extends Rank
  case object Five extends Rank
  case object Four extends Rank
  case object Three extends Rank
  case object Two extends Rank

  val all: Iterable[Rank] = List(Two, Three, Four, Five, Six, Seven, Eight, Nine, Ten, Jack, Queen, King, Ace)

  private def toInt(rank: Rank): Int = rank match {
    case Ace => 14
    case King => 13
    case Queen => 12
    case Jack => 11
    case Ten => 10
    case Nine => 9
    case Eight => 8
    case Seven => 7
    case Six => 6
    case Five => 5
    case Four => 4
    case Three => 3
    case Two => 2
  }

  implicit def orderingByRank[A <: Rank]: Ordering[A] =
    Ordering.by(r => toInt(r))
}