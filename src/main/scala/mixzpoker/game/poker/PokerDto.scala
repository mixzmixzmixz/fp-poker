package mixzpoker.game.poker

import io.circe.generic.JsonCodec

sealed trait PokerDto

object PokerDto {

  @JsonCodec
  case class CreateGameRequest(players: List[String])
}
