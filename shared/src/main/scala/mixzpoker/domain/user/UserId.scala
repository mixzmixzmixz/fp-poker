package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}

import scala.util.Random

final case class UserId(value: Int) extends AnyVal {
  override def toString: String = value.toString
}

object UserId {
  //todo creation as effect
  def fromRandom: UserId = UserId(Random.nextInt(100000) + 1)

  def fromString(str: String): Option[UserId] =
    str.toIntOption.map(UserId(_))

  def zero: UserId = UserId(0)


  implicit val encoderUserId: Encoder[UserId] =
    Encoder[String].contramap(a => a.toString)

  implicit val decoderUserId: Decoder[UserId] =
    Decoder[String].emap(s => UserId.fromString(s).toRight("wrong userId"))
}