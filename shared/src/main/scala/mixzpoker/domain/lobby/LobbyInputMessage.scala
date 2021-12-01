package mixzpoker.domain.lobby

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe._
import mixzpoker.domain.Token


sealed trait LobbyInputMessage

object LobbyInputMessage {

  case class Register(token: String) extends LobbyInputMessage
  case class Join(buyIn: Token) extends LobbyInputMessage
  case object Leave extends LobbyInputMessage
  case object Ready extends LobbyInputMessage
  case object NotReady extends LobbyInputMessage
  case class ChatMessage(message: String) extends LobbyInputMessage


  implicit val rDecoder: Decoder[Register] = deriveDecoder
  implicit val rEncoder: Encoder[Register] = deriveEncoder

  implicit val cmDecoder: Decoder[ChatMessage] = deriveDecoder
  implicit val cmEncoder: Encoder[ChatMessage] = deriveEncoder

  implicit val jDecoder: Decoder[Join] = deriveDecoder
  implicit val jEncoder: Encoder[Join] = deriveEncoder

  implicit val limDecoder: Decoder[LobbyInputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "Register"    => c.downField("params").as[Register]
    case "Leave"       => Right(Leave)
    case "Ready"       => Right(Ready)
    case "NotReady"    => Right(NotReady)
    case "Join"        => c.downField("params").as[Join]
    case "ChatMessage" => c.downField("params").as[ChatMessage]
    case _             => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[LobbyInputMessage] = Encoder.instance {
    case a: Register    => Json.obj("type" -> Json.fromString("Register"), "params" -> a.asJson)
    case Leave          => Json.obj("type" -> Json.fromString("Leave"))
    case Ready          => Json.obj("type" -> Json.fromString("Ready"))
    case NotReady       => Json.obj("type" -> Json.fromString("NotReady"))
    case a: Join        => Json.obj("type" -> Json.fromString("Join"), "params" -> a.asJson)
    case a: ChatMessage => Json.obj("type" -> Json.fromString("ChatMessage"), "params" -> a.asJson)
  }
}

/*
  {
    "id": "1122334455ddffee-aa11223344-adfafa",
    "timestamp": "11:22:33.123123-2012-12-12",
    "message": {
      "type": "join",
      "params": {
        "buyIn": 123123,
      }
    }
  }
 */