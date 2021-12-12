package mixzpoker.game

import mixzpoker.domain.game.GameId
import mixzpoker.lobby.LobbyName

//this is a record which maps a running getGame to the lobby it started in.
// It's probably gonna have some additional info as well
case class GameRecord(
  id: GameId,
  lobbyName: LobbyName
)
