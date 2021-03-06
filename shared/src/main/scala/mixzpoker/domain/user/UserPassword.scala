package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}


final case class UserPassword(value: String) extends AnyVal {
  override def toString: String = value
}

object UserPassword {
  //todo validation using cats Validated (here or in the backend)
  def fromString(str: String): UserPassword = UserPassword(str)

  def empty: UserPassword = UserPassword("")


  implicit val encoderUserId: Encoder[UserPassword] =
    Encoder[String].contramap(_.toString)

  implicit val decoderUserId: Decoder[UserPassword] =
    Decoder[String].emap(s => Right(UserPassword.fromString(s))) // todo check for correct pw
}
