package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}

import mixzpoker.domain.Token


sealed trait PokerInputMessage

object PokerInputMessage {

  final case object Ping extends PokerInputMessage
  final case class Join(buyIn: Token) extends PokerInputMessage
  final case object Leave extends PokerInputMessage

  final case object Fold extends PokerInputMessage
  final case object Check extends PokerInputMessage
  final case object Call extends PokerInputMessage
  final case class Raise(amount: Token) extends PokerInputMessage
  final case object AllIn extends PokerInputMessage

  implicit val jDecoder: Decoder[Join] = deriveDecoder
  implicit val jEncoder: Encoder[Join] = deriveEncoder

  implicit val raiseDecoder: Decoder[Raise] = deriveDecoder
  implicit val raiseEncoder: Encoder[Raise] = deriveEncoder


  implicit val ppeDecoder: Decoder[PokerInputMessage] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "Ping"  => Right(Ping)
    case "Join"  => c.downField("params").as[Join]
    case "Leave" => Right(Leave)
    case "Fold"  => Right(Fold)
    case "Check" => Right(Check)
    case "AllIn" => Right(AllIn)
    case "Call"  => Right(Call)
    case "Raise" => c.downField("params").as[Raise]
    case _       => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val ppeEncoder: Encoder[PokerInputMessage] = Encoder.instance {
    case Ping     => Json.obj("type" -> "Ping".asJson)
    case a: Join  => Json.obj("type" -> "Join".asJson, "params" -> a.asJson)
    case Leave    => Json.obj("type" -> "Leave".asJson)
    case Fold     => Json.obj("type" -> "Fold".asJson)
    case Check    => Json.obj("type" -> "Check".asJson)
    case AllIn    => Json.obj("type" -> "AllIn".asJson)
    case Call     => Json.obj("type" -> "Call".asJson)
    case a: Raise => Json.obj("type" -> "Raise".asJson, "params" -> a.asJson)
  }
}
