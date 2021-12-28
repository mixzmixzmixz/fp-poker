package mixzpoker.domain.game.poker

import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, KeyDecoder}
import mixzpoker.domain.Token
import mixzpoker.domain.game.core.{Card, Deck, Hand}
import mixzpoker.domain.user.{UserId, UserName}


sealed trait PokerEvent

object PokerEvent {
  @JsonCodec
  final case class PlayerJoinedEvent(userId: UserId, buyIn: Token, name: UserName) extends PokerEvent
  @JsonCodec
  final case class PlayerLeftEvent(userId: UserId, name: UserName, tokens: Token) extends PokerEvent

  @JsonCodec
  final case class PlayerFoldedEvent(userId: UserId) extends PokerEvent
  @JsonCodec
  final case class PlayerCheckedEvent(userId: UserId) extends PokerEvent
  @JsonCodec
  final case class PlayerCalledEvent(userId: UserId) extends PokerEvent
  @JsonCodec
  final case class PlayerRaisedEvent(userId: UserId, amount: Token) extends PokerEvent
  @JsonCodec
  final case class PlayerAllInedEvent(userId: UserId) extends PokerEvent

  @JsonCodec
  final case class NewRoundStartedEvent(deck: Deck) extends PokerEvent
  @JsonCodec
  final case class CardsDealtEvent(playersWithCards: Map[UserId, Hand], deck: Deck) extends PokerEvent
  @JsonCodec
  final case class FlopStartedEvent(card1: Card, card2: Card, card3: Card, deck: Deck) extends PokerEvent
  @JsonCodec
  final case class TurnStartedEvent(card: Card, deck: Deck) extends PokerEvent
  @JsonCodec
  final case class RiverStartedEvent(card: Card, deck: Deck) extends PokerEvent

  final case object RoundFinishedEvent extends PokerEvent

  final case object GameFinishedEvent extends PokerEvent


  implicit val keyDecoderUserId: KeyDecoder[UserId] = (key: String) => UserId.fromString(key)
  implicit val mapUserIdTokenEncoder: Encoder[Map[UserId, Hand]] =
    (map: Map[UserId, Hand]) =>
      Json.obj(map.map { case (id, token) => id.toString -> token.asJson }.toList: _*)

  implicit val mapUserIdTokenDecoder: Decoder[Map[UserId, Hand]] = Decoder.decodeMap[UserId, Hand]


  implicit val pgeDecoder: Decoder[PokerEvent] = (c: HCursor) => c.downField("type").as[String].flatMap {
    case "PlayerJoinedEvent"    => c.downField("params").as[PlayerJoinedEvent]
    case "PlayerLeftEvent"      => c.downField("params").as[PlayerLeftEvent]

    case "PlayerFoldedEvent"    => c.downField("params").as[PlayerFoldedEvent]
    case "PlayerCheckedEvent"   => c.downField("params").as[PlayerCheckedEvent]
    case "PlayerCalledEvent"    => c.downField("params").as[PlayerCalledEvent]
    case "PlayerRaisedEvent"    => c.downField("params").as[PlayerRaisedEvent]
    case "PlayerAllInedEvent"   => c.downField("params").as[PlayerAllInedEvent]

    case "NewRoundStartedEvent" => c.downField("params").as[NewRoundStartedEvent]
    case "CardsDealtEvent"      => c.downField("params").as[CardsDealtEvent]
    case "FlopStartedEvent"     => c.downField("params").as[FlopStartedEvent]
    case "TurnStartedEvent"     => c.downField("params").as[TurnStartedEvent]
    case "RiverStartedEvent"    => c.downField("params").as[RiverStartedEvent]
    case "RoundFinishedEvent"   => Right(RoundFinishedEvent)
    case "GameFinishedEvent"    => Right(GameFinishedEvent)

    case _                      => Left(DecodingFailure("Invalid message type", List()))
  }

  implicit val pgeEncoder: Encoder[PokerEvent] = Encoder.instance {
    case a: PlayerJoinedEvent    => Json.obj("type" -> "PlayerJoinedEvent".asJson,   "params" -> a.asJson)
    case a: PlayerLeftEvent      => Json.obj("type" -> "PlayerLeftEvent".asJson,     "params" -> a.asJson)

    case a: PlayerFoldedEvent    => Json.obj("type" -> "PlayerFoldedEvent".asJson,   "params" -> a.asJson)
    case a: PlayerCheckedEvent   => Json.obj("type" -> "PlayerCheckedEvent".asJson,  "params" -> a.asJson)
    case a: PlayerCalledEvent    => Json.obj("type" -> "PlayerCalledEvent".asJson,   "params" -> a.asJson)
    case a: PlayerRaisedEvent    => Json.obj("type" -> "PlayerRaisedEvent".asJson,   "params" -> a.asJson)
    case a: PlayerAllInedEvent   => Json.obj("type" -> "PlayerAllInedEvent".asJson,  "params" -> a.asJson)

    case a: NewRoundStartedEvent => Json.obj("type" -> "NewRoundStartedEvent".asJson, "params" -> a.asJson)
    case a: CardsDealtEvent      => Json.obj("type" -> "CardsDealtEvent".asJson,      "params" -> a.asJson)
    case a: FlopStartedEvent     => Json.obj("type" -> "FlopStartedEvent".asJson,     "params" -> a.asJson)
    case a: TurnStartedEvent     => Json.obj("type" -> "TurnStartedEvent".asJson,     "params" -> a.asJson)
    case a: RiverStartedEvent    => Json.obj("type" -> "RiverStartedEvent".asJson,    "params" -> a.asJson)
    case RoundFinishedEvent      => Json.obj("type" -> "RoundFinishedEvent".asJson)
    case GameFinishedEvent       => Json.obj("type" -> "GameFinishedEvent".asJson)
  }
}
