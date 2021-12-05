package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

sealed trait PokerOutputMessage

object PokerOutputMessage {
  case class ErrorMessage(message: String) extends PokerOutputMessage


  implicit val rDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val rEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val limDecoder: Decoder[PokerOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "ErrorMessage" => c.downField("params").as[ErrorMessage]
    case _              => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[PokerOutputMessage] = Encoder.instance {
    case a: ErrorMessage => Json.obj("type" -> "ErrorMessage".asJson, "params" -> a.asJson)
  }
}
