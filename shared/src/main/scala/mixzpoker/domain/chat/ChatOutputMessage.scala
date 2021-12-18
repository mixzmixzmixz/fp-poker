package mixzpoker.domain.chat

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.user.User


sealed trait ChatOutputMessage

object ChatOutputMessage {
  final case class ChatMessageFrom(message: String, user: User) extends ChatOutputMessage
  final case class ErrorMessage(message: String) extends ChatOutputMessage
  final case object KeepAlive extends ChatOutputMessage


  implicit val cmDecoder: Decoder[ChatMessageFrom] = deriveDecoder
  implicit val cmEncoder: Encoder[ChatMessageFrom] = deriveEncoder

  implicit val emDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val emEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val lomDecoder: Decoder[ChatOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "ChatMessageFrom" => c.downField("params").as[ChatMessageFrom]
    case "ErrorMessage"    => c.downField("params").as[ErrorMessage]
    case "KeepAlive"       => Right(KeepAlive)
  }

  implicit val lomEncoder: Encoder[ChatOutputMessage] = Encoder.instance {
    case KeepAlive          => Json.obj("type" -> "KeepAlive".asJson)
    case a: ErrorMessage    => Json.obj("type" -> "ErrorMessage".asJson, "params" -> a.asJson)
    case a: ChatMessageFrom => Json.obj("type" -> "ChatMessageFrom".asJson, "params" -> a.asJson)
  }
}