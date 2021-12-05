package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.game.{GameEventId, GameId}

case class PokerEventContext(
  id: GameEventId,
  gameId: GameId,
  event: PokerEvent
)

object PokerEventContext {
  implicit val pecEncoder: Encoder[PokerEventContext] = deriveEncoder
  implicit val pecDecoder: Decoder[PokerEventContext] = deriveDecoder
}