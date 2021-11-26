package mixzpoker.messages.lobby

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

sealed trait LobbyInputMessage

object LobbyInputMessage {
  case object Disconnect extends LobbyInputMessage
  case class InvalidMessage(reason: String) extends LobbyInputMessage

  implicit val limDecoder: Decoder[LobbyInputMessage] = deriveDecoder
  implicit val limEncoder: Encoder[LobbyInputMessage] = deriveEncoder
}
