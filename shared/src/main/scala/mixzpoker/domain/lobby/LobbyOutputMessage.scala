package mixzpoker.domain.lobby

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe._
import io.circe.syntax._
import mixzpoker.domain.user.UserDto.UserDto
import mixzpoker.domain.lobby.LobbyDto.LobbyDto

sealed trait LobbyOutputMessage

object LobbyOutputMessage {
  case object KeepAlive extends LobbyOutputMessage
  case class LobbyState(lobby: LobbyDto) extends LobbyOutputMessage
  case class GameStarted(gameId: String) extends LobbyOutputMessage
  case class ErrorMessage(message: String) extends LobbyOutputMessage


  implicit val lsDecoder: Decoder[LobbyState] = deriveDecoder
  implicit val lsEncoder: Encoder[LobbyState] = deriveEncoder

  implicit val gsDecoder: Decoder[GameStarted] = deriveDecoder
  implicit val gsEncoder: Encoder[GameStarted] = deriveEncoder

  implicit val emDecoder: Decoder[ErrorMessage] = deriveDecoder
  implicit val emEncoder: Encoder[ErrorMessage] = deriveEncoder

  implicit val lomDecoder: Decoder[LobbyOutputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "KeepAlive"       => Right(KeepAlive)
    case "LobbyState"      => c.downField("params").as[LobbyState]
    case "GameStarted"     => c.downField("params").as[GameStarted]
    case "LobbyError"      => c.downField("params").as[ErrorMessage]
    case _                 => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val lomEncoder: Encoder[LobbyOutputMessage] = Encoder.instance {
    case KeepAlive          => Json.obj("type" -> "KeepAlive".asJson)
    case a: LobbyState      => Json.obj("type" -> "LobbyState".asJson,      "params" -> a.asJson)
    case a: GameStarted     => Json.obj("type" -> "GameStarted".asJson,     "params" -> a.asJson)
    case a: ErrorMessage    => Json.obj("type" -> "ErrorMessage".asJson,    "params" -> a.asJson)
  }
}
