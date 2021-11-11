package mixzpoker.game.poker

import cats.syntax.functor._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.generic.auto._
import mixzpoker.domain.Token
import mixzpoker.game.{EventId, GameId}
import mixzpoker.game.poker.game.PokerGameEvent
import mixzpoker.user.UserId

sealed trait PokerEvent {
  def id: EventId
  //time
}

object PokerEvent {
  @JsonCodec
  case class GameEvent(
    id: EventId,
    gameId: GameId,
    event: PokerGameEvent // todo different events
    //time
  ) extends PokerEvent

  @JsonCodec
  case class CreateGameEvent(
    id: EventId,
    gameId: GameId,
    users: List[(UserId, Token)],
    settings: PokerSettings
  ) extends PokerEvent

  implicit val decodeEvent: Decoder[PokerEvent] = List[Decoder[PokerEvent]](
    Decoder[GameEvent].widen,
    Decoder[CreateGameEvent].widen
  ).reduceLeft(_ or _)
}



