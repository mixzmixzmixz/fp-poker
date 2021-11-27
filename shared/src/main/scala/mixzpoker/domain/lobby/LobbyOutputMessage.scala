package mixzpoker.domain.lobby

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe._
import io.circe.syntax._
import mixzpoker.domain.lobby.LobbyDto.LobbyDto

sealed trait LobbyOutputMessage

object LobbyOutputMessage {
  case object KeepAlive extends LobbyOutputMessage
  case class LobbyState(lobby: LobbyDto) extends LobbyOutputMessage


  implicit val lsDecoder: Decoder[LobbyState] = deriveDecoder
  implicit val lsEncoder: Encoder[LobbyState] = deriveEncoder

  implicit val lomDecoder: Decoder[LobbyOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "KeepAlive"      => Right(KeepAlive)
    case "LobbyState"     => c.downField("params").as[LobbyState]
    case _                => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val lomEncoder: Encoder[LobbyOutputMessage] = Encoder.instance {
    case KeepAlive     => Json.obj("type" -> Json.fromString("KeepAlive"))
    case a: LobbyState => Json.obj("type" -> Json.fromString("LobbyState"), "params" -> a.asJson)
  }
}
