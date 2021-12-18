package mixzpoker.game

import mixzpoker.domain.game.GameId
import mixzpoker.domain.lobby.LobbyName

//this is a record which maps a running getGame to the lobby it started in.
// It's probably gonna have some additional info as well
final case class GameRecord(
  id: GameId,
  lobbyName: LobbyName
)
