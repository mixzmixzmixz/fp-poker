package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import mixzpoker.domain.Token

sealed trait PokerInputMessage

object PokerInputMessage {
  case class Join(buyIn: Token) extends PokerInputMessage
  case object Leave extends PokerInputMessage

  case object Fold extends PokerInputMessage
  case object Check extends PokerInputMessage
  case class Call(amount: Token) extends PokerInputMessage
  case class Raise(amount: Token) extends PokerInputMessage
  case class AllIn(amount: Token) extends PokerInputMessage


  implicit val jDecoder: Decoder[Join] = deriveDecoder
  implicit val jEncoder: Encoder[Join] = deriveEncoder

  implicit val callDecoder: Decoder[Call] = deriveDecoder
  implicit val callEncoder: Encoder[Call] = deriveEncoder

  implicit val raiseDecoder: Decoder[Raise] = deriveDecoder
  implicit val raiseEncoder: Encoder[Raise] = deriveEncoder

  implicit val allinDecoder: Decoder[AllIn] = deriveDecoder
  implicit val alliEncoder: Encoder[AllIn] = deriveEncoder


  implicit val limDecoder: Decoder[PokerInputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "Join"     => c.downField("params").as[Join]
    case "Leave"    => Right(Leave)
    case "Fold"     => Right(Fold)
    case "Check"    => Right(Check)
    case "Call"     => c.downField("params").as[Call]
    case "Raise"    => c.downField("params").as[Raise]
    case "AllIn"    => c.downField("params").as[AllIn]
    case _          => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val limEncoder: Encoder[PokerInputMessage] = Encoder.instance {
    case a: Join     => Json.obj("type" -> "Join".asJson, "params" -> a.asJson)
    case Leave       => Json.obj("type" -> "Leave".asJson)
    case Fold        => Json.obj("type" -> "Fold".asJson)
    case Check       => Json.obj("type" -> "Check".asJson)
    case a: Call     => Json.obj("type" -> "Call".asJson, "params" -> a.asJson)
    case a: Raise    => Json.obj("type" -> "Raise".asJson, "params" -> a.asJson)
    case a: AllIn    => Json.obj("type" -> "AllIn".asJson, "params" -> a.asJson)
  }
}
