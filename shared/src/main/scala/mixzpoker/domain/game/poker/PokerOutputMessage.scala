package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import mixzpoker.domain.user.UserId

sealed trait PokerOutputMessage

object PokerOutputMessage {
  case class ErrorMessage(message: String) extends PokerOutputMessage
  case class GameState(game: PokerGame) extends PokerOutputMessage
  case class RoundStart(num: Int) extends PokerOutputMessage
  case class PlayerToAction(id: UserId, secondsForAction: Int) extends PokerOutputMessage


  implicit val emDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val emEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val gsDecoder: Decoder[GameState] = deriveDecoder
  implicit val gsEncoder: Encoder[GameState] = deriveEncoder

  implicit val rsDecoder: Decoder[RoundStart] = deriveDecoder
  implicit val rsEncoder: Encoder[RoundStart] = deriveEncoder

  implicit val ptaDecoder: Decoder[PlayerToAction] = deriveDecoder
  implicit val ptaEncoder: Encoder[PlayerToAction] = deriveEncoder

  implicit val limDecoder: Decoder[PokerOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "ErrorMessage"   => c.downField("params").as[ErrorMessage]
    case "GameState"      => c.downField("params").as[GameState]
    case "RoundStart"     => c.downField("params").as[RoundStart]
    case "PlayerToAction" => c.downField("params").as[PlayerToAction]
    case _                => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[PokerOutputMessage] = Encoder.instance {
    case a: ErrorMessage   => Json.obj("type" -> "ErrorMessage".asJson, "params" -> a.asJson)
    case a: GameState      => Json.obj("type" -> "GameState".asJson, "params" -> a.asJson)
    case a: RoundStart     => Json.obj("type" -> "RoundStart".asJson, "params" -> a.asJson)
    case a: PlayerToAction => Json.obj("type" -> "PlayerToAction".asJson, "params" -> a.asJson)
  }
}
