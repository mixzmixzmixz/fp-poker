package mixzpoker.domain.lobby

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe._
import mixzpoker.domain.Token


sealed trait LobbyInputMessage

object LobbyInputMessage {
  case class Join(buyIn: Token) extends LobbyInputMessage
  case object Leave extends LobbyInputMessage

  implicit val jDecoder: Decoder[Join] = deriveDecoder
  implicit val jEncoder: Encoder[Join] = deriveEncoder

  implicit val limDecoder: Decoder[LobbyInputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "Leave" => Right(Leave)
    case "Join"  => c.downField("params").as[Join]
    case _       => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[LobbyInputMessage] = Encoder.instance {
    case Leave   => Json.obj("type" -> Json.fromString("Leave"))
    case a: Join => Json.obj("type" -> Json.fromString("Join"), "params" -> a.asJson)
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