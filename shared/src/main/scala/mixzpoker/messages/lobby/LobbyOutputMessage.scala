package mixzpoker.messages.lobby

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

sealed trait LobbyOutputMessage

object LobbyOutputMessage {
  case object Initial extends LobbyOutputMessage
  case object KeepAlive extends LobbyOutputMessage

  implicit val lomDecoder: Decoder[LobbyOutputMessage] = deriveDecoder
  implicit val lomEncoder: Encoder[LobbyOutputMessage] = deriveEncoder
}
