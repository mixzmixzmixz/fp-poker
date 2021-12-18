package mixzpoker.domain.chat

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}


sealed trait ChatInputMessage

object ChatInputMessage {
  final case class ChatMessage(message: String) extends ChatInputMessage


  implicit val cmDecoder: Decoder[ChatMessage] = deriveDecoder
  implicit val cmEncoder: Encoder[ChatMessage] = deriveEncoder

  implicit val limDecoder: Decoder[ChatInputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "ChatMessage" => c.downField("params").as[ChatMessage]
  }

  implicit val limEncoder: Encoder[ChatInputMessage] = Encoder.instance {
    case a: ChatMessage => Json.obj("type" -> Json.fromString("ChatMessage"), "params" -> a.asJson)
  }
}
