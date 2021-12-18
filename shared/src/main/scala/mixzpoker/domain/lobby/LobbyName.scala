package mixzpoker.domain.lobby

import io.circe.{Decoder, Encoder}


final case class LobbyName(value: String) extends AnyVal {
  override def toString: String = value
}

object LobbyName {
  def fromString(string: String): Option[LobbyName] = Some(LobbyName(string))


  implicit val encoderUserId: Encoder[LobbyName] =
    Encoder[String].contramap(_.toString)

  implicit val decoderUserId: Decoder[LobbyName] =
    Decoder[String].emap(s => LobbyName.fromString(s).toRight("invalid lobby name")) // todo check for correct pw
}