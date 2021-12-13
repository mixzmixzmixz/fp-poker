package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}

sealed trait Rank { def show: String }

object Rank {
  case object Ace extends Rank    { def show: String = "A" }
  case object King extends Rank   { def show: String = "K" }
  case object Queen extends Rank  { def show: String = "Q" }
  case object Jack extends Rank   { def show: String = "J" }
  case object Ten extends Rank    { def show: String = "10"}
  case object Nine extends Rank   { def show: String = "9" }
  case object Eight extends Rank  { def show: String = "8" }
  case object Seven extends Rank  { def show: String = "7" }
  case object Six extends Rank    { def show: String = "6" }
  case object Five extends Rank   { def show: String = "5" }
  case object Four extends Rank   { def show: String = "4" }
  case object Three extends Rank  { def show: String = "3" }
  case object Two extends Rank    { def show: String = "2" }

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

  implicit val rankEncoder: Encoder[Rank] = Encoder[String].contramap { _.show }

  implicit val rankDecoder: Decoder[Rank] = Decoder[String].emap {
    case "A" => Right(Ace)
    case "K" => Right(King)
    case "Q" => Right(Queen)
    case "J" => Right(Jack)
    case "10" => Right(Ten)
    case "9" => Right(Nine)
    case "8" => Right(Eight)
    case "7" => Right(Seven)
    case "6" => Right(Six)
    case "5" => Right(Five)
    case "4" => Right(Four)
    case "3" => Right(Three)
    case "2" => Right(Two)
    case _ => Left("wrong rank")

  }

}