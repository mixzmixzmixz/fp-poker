package mixzpoker.lobby

import mixzpoker.domain.lobby.LobbyInputMessage
import mixzpoker.user.User

case class LobbyEvent(user: User, lobbyName: LobbyName, message: LobbyInputMessage)
