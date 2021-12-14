package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import mixzpoker.domain.user.UserId

sealed trait PokerOutputMessage

object PokerOutputMessage {
  case class ErrorMessage(toUser: Option[UserId], message: String) extends PokerOutputMessage
  case class GameState(game: PokerGame) extends PokerOutputMessage
  case class LogMessage(message: String) extends PokerOutputMessage
  case class ShowdownWin(showdown: Showdown) extends PokerOutputMessage
  case class NoShowdownWin(player: PokerPlayer) extends PokerOutputMessage
  case class PlayerToAction(id: UserId, secondsForAction: Int) extends PokerOutputMessage


  implicit val emDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val emEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val gsDecoder: Decoder[GameState] = deriveDecoder
  implicit val gsEncoder: Encoder[GameState] = deriveEncoder

  implicit val lmDecoder: Decoder[LogMessage] = deriveDecoder
  implicit val lmEncoder: Encoder[LogMessage] = deriveEncoder

  implicit val swDecoder: Decoder[ShowdownWin] = deriveDecoder
  implicit val swEncoder: Encoder[ShowdownWin] = deriveEncoder

  implicit val nswDecoder: Decoder[NoShowdownWin] = deriveDecoder
  implicit val nswEncoder: Encoder[NoShowdownWin] = deriveEncoder

  implicit val ptaDecoder: Decoder[PlayerToAction] = deriveDecoder
  implicit val ptaEncoder: Encoder[PlayerToAction] = deriveEncoder

  implicit val limDecoder: Decoder[PokerOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "ErrorMessage"   => c.downField("params").as[ErrorMessage]
    case "GameState"      => c.downField("params").as[GameState]
    case "LogMessage"     => c.downField("params").as[LogMessage]
    case "ShowdownWin"    => c.downField("params").as[ShowdownWin]
    case "NoShowdownWin"  => c.downField("params").as[NoShowdownWin]
    case "PlayerToAction" => c.downField("params").as[PlayerToAction]
    case _                => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[PokerOutputMessage] = Encoder.instance {
    case a: ErrorMessage   => Json.obj("type" -> "ErrorMessage".asJson, "params" -> a.asJson)
    case a: GameState      => Json.obj("type" -> "GameState".asJson, "params" -> a.asJson)
    case a: LogMessage     => Json.obj("type" -> "LogMessage".asJson, "params" -> a.asJson)
    case a: ShowdownWin    => Json.obj("type" -> "ShowdownWin".asJson, "params" -> a.asJson)
    case a: NoShowdownWin  => Json.obj("type" -> "NoShowdownWin".asJson, "params" -> a.asJson)
    case a: PlayerToAction => Json.obj("type" -> "PlayerToAction".asJson, "params" -> a.asJson)
  }
}
