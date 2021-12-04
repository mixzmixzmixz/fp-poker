package mixzpoker.game.poker

import cats.syntax.functor._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import mixzpoker.game.{EventId, GameId}
import mixzpoker.game.poker.game.PokerGameEvent

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

  implicit val decodeEvent: Decoder[PokerEvent] = List[Decoder[PokerEvent]](
    Decoder[GameEvent].widen
  ).reduceLeft(_ or _)
}



