package mixzpoker.domain.game.poker

import cats.syntax.functor._
import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import mixzpoker.domain.Token
import mixzpoker.domain.user.UserName


sealed trait PokerEvent

object PokerEvent {
  sealed trait PokerPlayerEvent extends PokerEvent  //created by players
  sealed trait PokerGameEvent extends PokerEvent    // created by game

  case object Ping extends PokerPlayerEvent // todo not really a poker event though
  case class Join(buyIn: Token, name: UserName) extends PokerPlayerEvent // todo username shouldn't come from the frontend
  case object Leave extends PokerPlayerEvent

  case object Fold extends PokerPlayerEvent
  case object Check extends PokerPlayerEvent
  case object Call extends PokerPlayerEvent
  case class Raise(amount: Token) extends PokerPlayerEvent
  case object AllIn extends PokerPlayerEvent

  case class NextState(state: PokerGameState) extends PokerGameEvent


  implicit val jDecoder: Decoder[Join] = deriveDecoder
  implicit val jEncoder: Encoder[Join] = deriveEncoder

  implicit val raiseDecoder: Decoder[Raise] = deriveDecoder
  implicit val raiseEncoder: Encoder[Raise] = deriveEncoder

  implicit val nextStateDecoder: Decoder[NextState] = deriveDecoder
  implicit val nextStateEncoder: Encoder[NextState] = deriveEncoder


  implicit val ppeDecoder: Decoder[PokerPlayerEvent] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "Ping"     => Right(Ping)
    case "Join"     => c.downField("params").as[Join]
    case "Leave"    => Right(Leave)
    case "Fold"     => Right(Fold)
    case "Check"    => Right(Check)
    case "AllIn"    => Right(AllIn)
    case "Call"     => Right(Call)
    case "Raise"    => c.downField("params").as[Raise]
    case _          => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val ppeEncoder: Encoder[PokerPlayerEvent] = Encoder.instance {
    case Ping        => Json.obj("type" -> "Ping".asJson)
    case a: Join     => Json.obj("type" -> "Join".asJson, "params" -> a.asJson)
    case Leave       => Json.obj("type" -> "Leave".asJson)
    case Fold        => Json.obj("type" -> "Fold".asJson)
    case Check       => Json.obj("type" -> "Check".asJson)
    case AllIn       => Json.obj("type" -> "AllIn".asJson)
    case Call        => Json.obj("type" -> "Call".asJson)
    case a: Raise    => Json.obj("type" -> "Raise".asJson, "params" -> a.asJson)
  }

  implicit val pgeDecoder: Decoder[PokerGameEvent] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "NextState" => c.downField("params").as[NextState]
    case _           => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val pgeEncoder: Encoder[PokerGameEvent] = Encoder.instance {
    case a: NextState => Json.obj("type" -> "NextState".asJson, "params" -> a.asJson)
  }

  implicit val pokerEventEncoder: Encoder[PokerEvent] = Encoder.instance {
    case ppe: PokerPlayerEvent => ppe.asJson
    case pge: PokerGameEvent   => pge.asJson
  }

  implicit val pokerEventDecoder: Decoder[PokerEvent] =
    Decoder[PokerPlayerEvent].widen or Decoder[PokerGameEvent].widen

}
