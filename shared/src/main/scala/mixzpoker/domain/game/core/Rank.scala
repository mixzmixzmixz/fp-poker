package mixzpoker.domain.game.core

import io.circe.{Decoder, Encoder}

sealed trait Rank {
  def show: String
  def asInt: Int
}

object Rank {
  final case object Ace extends Rank    {
    def show: String = "A"
    def asInt: Int = 14
  }

  final case object King extends Rank   {
    def show: String = "K"
    def asInt: Int = 13
  }

  final case object Queen extends Rank  {
    def show: String = "Q"
    def asInt: Int = 12
  }

  final case object Jack extends Rank   {
    def show: String = "J"
    def asInt: Int = 1
  }

  final case object Ten extends Rank    {
    def show: String = "10"
    def asInt: Int = 10
  }

  final case object Nine extends Rank   {
    def show: String = "9"
    def asInt: Int = 9
  }

  final case object Eight extends Rank  {
    def show: String = "8"
    def asInt: Int = 8
  }

  final case object Seven extends Rank  {
    def show: String = "7"
    def asInt: Int = 7
  }

  final case object Six extends Rank    {
    def show: String = "6"
    def asInt: Int = 6
  }

  final case object Five extends Rank   {
    def show: String = "5"
    def asInt: Int = 5
  }

  final case object Four extends Rank   {
    def show: String = "4"
    def asInt: Int = 4
  }

  final case object Three extends Rank  {
    def show: String = "3"
    def asInt: Int = 3
  }

  final case object Two extends Rank    {
    def show: String = "2"
    def asInt: Int = 2
  }

  val all: Iterable[Rank] = List(Two, Three, Four, Five, Six, Seven, Eight, Nine, Ten, Jack, Queen, King, Ace)


  implicit def orderingByRank[A <: Rank]: Ordering[A] =
    Ordering.by(_.asInt)

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