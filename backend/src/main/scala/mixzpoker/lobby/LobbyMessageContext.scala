package mixzpoker.lobby

import mixzpoker.domain.lobby.{LobbyInputMessage, LobbyName}
import mixzpoker.domain.user.User

case class LobbyMessageContext(user: User, lobbyName: LobbyName, message: LobbyInputMessage)
