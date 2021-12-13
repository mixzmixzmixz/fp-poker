package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}

case class UserName(value: String) extends AnyVal {
  override def toString: String = value
}

object UserName {
  def empty: UserName = UserName("--")

  def fromString(str: String): Option[UserName] =
    Some(UserName(str))

  implicit val encoderUserName: Encoder[UserName] =
    Encoder[String].contramap(a => a.toString)

  implicit val decoderUserName: Decoder[UserName] =
    Decoder[String].emap(s => UserName.fromString(s).toRight("wrong user name"))
}