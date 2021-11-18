package mixzpoker.model

import mixzpoker.model.GameSettings.PokerSettings
import mixzpoker.model.UserDto.UserDto

object LobbyDto {

  case class Lobby(
    name: String, owner: UserDto, users: List[(UserDto, Int)],
    gameType: GameType, gameSettings: PokerSettings
  )

}
