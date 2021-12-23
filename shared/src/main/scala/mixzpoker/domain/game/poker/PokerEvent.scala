package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import mixzpoker.domain.Token
import mixzpoker.domain.game.core.{Card, Deck, Hand}
import mixzpoker.domain.user.{UserId, UserName}


sealed trait PokerEvent

object PokerEvent {
  final case class PlayerJoinedEvent(userId: UserId, buyIn: Token, name: UserName) extends PokerEvent
  final case class PlayerLeftEvent(userId: UserId) extends PokerEvent

  final case class PlayerFoldedEvent(userId: UserId) extends PokerEvent
  final case class PlayerCheckedEvent(userId: UserId) extends PokerEvent
  final case class PlayerCalledEvent(userId: UserId) extends PokerEvent
  final case class PlayerRaisedEvent(userId: UserId, amount: Token) extends PokerEvent
  final case class PlayerAllInedEvent(userId: UserId) extends PokerEvent

  final case class NewRoundStartedEvent(deck: Deck) extends PokerEvent
  final case class CardsDealtEvent(playersWithCards: Map[UserId, Hand], deck: Deck) extends PokerEvent
  final case class FlopStartedEvent(card1: Card, card2: Card, card3: Card, deck: Deck) extends PokerEvent
  final case class TurnStartedEvent(card: Card, deck: Deck) extends PokerEvent
  final case class RiverStartedEvent(card: Card, deck: Deck) extends PokerEvent
  final case object RoundFinishedEvent extends PokerEvent




  implicit val newRoundStartedDecoder: Decoder[NewRoundStartedEvent] = deriveDecoder
  implicit val newRoundStartedEncoder: Encoder[NewRoundStartedEvent] = deriveEncoder


  implicit val pgeDecoder: Decoder[PokerEvent] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "NewRoundStartedEvent" => c.downField("params").as[NewRoundStartedEvent]
    case _                 => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val pgeEncoder: Encoder[PokerEvent] = Encoder.instance {
    case a: NewRoundStartedEvent => Json.obj("type" -> "NewRoundStartedEvent".asJson, "params" -> a.asJson)
  }
}
