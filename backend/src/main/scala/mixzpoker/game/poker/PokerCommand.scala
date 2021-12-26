package mixzpoker.game.poker

import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.JsonCodec
import mixzpoker.domain.Token
import mixzpoker.domain.user.{UserId, UserName}


sealed trait PokerCommand

object PokerCommand {
  @JsonCodec
  final case class PingCommand(userId: UserId) extends PokerCommand
  @JsonCodec
  final case class JoinCommand(userId: UserId, buyIn: Token, name: UserName) extends PokerCommand
  @JsonCodec
  final case class LeaveCommand(userId: UserId) extends PokerCommand

  @JsonCodec
  final case class FoldCommand(userId: UserId) extends PokerCommand
  @JsonCodec
  final case class CheckCommand(userId: UserId) extends PokerCommand
  @JsonCodec
  final case class CallCommand(userId: UserId) extends PokerCommand
  @JsonCodec
  final case class RaiseCommand(userId: UserId, amount: Token) extends PokerCommand
  @JsonCodec
  final case class AllInCommand(userId: UserId) extends PokerCommand




  implicit val pokerCommandDecoder: Decoder[PokerCommand] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "PingCommand"  => c.downField("params").as[PingCommand]
    case "JoinCommand"  => c.downField("params").as[JoinCommand]
    case "LeaveCommand" => c.downField("params").as[LeaveCommand]

    case "FoldCommand"  => c.downField("params").as[FoldCommand]
    case "CheckCommand" => c.downField("params").as[CheckCommand]
    case "CallCommand"  => c.downField("params").as[CallCommand]
    case "RaiseCommand" => c.downField("params").as[RaiseCommand]
    case "AllInCommand" => c.downField("params").as[AllInCommand]

    case _              => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val pokerCommandEncoder: Encoder[PokerCommand] = Encoder.instance {
    case a: PingCommand  => Json.obj("type" -> "PingCommand".asJson,  "params" -> a.asJson)
    case a: JoinCommand  => Json.obj("type" -> "JoinCommand".asJson,  "params" -> a.asJson)
    case a: LeaveCommand => Json.obj("type" -> "LeaveCommand".asJson, "params" -> a.asJson)

    case a: FoldCommand  => Json.obj("type" -> "FoldCommand".asJson,  "params" -> a.asJson)
    case a: CheckCommand => Json.obj("type" -> "CheckCommand".asJson, "params" -> a.asJson)
    case a: CallCommand  => Json.obj("type" -> "CallCommand".asJson,  "params" -> a.asJson)
    case a: RaiseCommand => Json.obj("type" -> "RaiseCommand".asJson, "params" -> a.asJson)
    case a: AllInCommand => Json.obj("type" -> "AllInCommand".asJson, "params" -> a.asJson)
  }
}

