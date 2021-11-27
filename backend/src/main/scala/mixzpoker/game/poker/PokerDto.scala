package mixzpoker.game.poker

import io.circe.generic.JsonCodec
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.game.core.Card
import mixzpoker.game.poker.game.{PokerGame, Pot}

object PokerDto {

  @JsonCodec(encodeOnly = true)
  case class GameDto(
    id: String,
    board: List[Card],
    pot: Pot,
    settings: PokerSettings
  )

  def fromPokerGame(game: PokerGame): GameDto =
    GameDto(
      id = game.id.toString,
      board = game.board,
      pot = game.pot,
      settings = game.settings,
    )

}
