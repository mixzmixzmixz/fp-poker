package mixzpoker

import io.circe.{Decoder, Encoder}

import scala.util.Random
import mixzpoker.user.UserError._

package object user {

  case class UserId(value: Int) extends AnyVal {
    override def toString: String = value.toString
  }

  object UserId {
    def fromRandom: UserId = UserId(Random.nextInt(100000) + 1)

    def fromString(str: String): Option[UserId] =
      str.toIntOption.map(UserId(_))


    implicit val encoderUserId: Encoder[UserId] =
      Encoder[String].contramap(a => a.toString)

    implicit val decoderUserId: Decoder[UserId] =
      Decoder[String].emap(s => UserId.fromString(s).toRight("wrong userId"))
  }

  case class UserName(value: String) extends AnyVal {
    override def toString: String = value
  }

  case class UserPassword(value: String) extends AnyVal

  object UserPassword {
    def fromString(str: String): ErrOr[UserPassword] =
      Right(UserPassword(str))
  }


}
