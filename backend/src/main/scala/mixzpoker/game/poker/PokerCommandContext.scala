package mixzpoker.game.poker

import mixzpoker.domain.game.GameId


final case class PokerCommandContext(
  gameId: GameId,
  command: PokerCommand
)
