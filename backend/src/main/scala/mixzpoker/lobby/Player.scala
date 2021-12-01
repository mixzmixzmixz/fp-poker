package mixzpoker.lobby

import mixzpoker.domain.Token
import mixzpoker.domain.lobby.LobbyDto.PlayerDto
import mixzpoker.user.User

case class Player(
  user: User,
  buyIn: Token,
  ready: Boolean = false
) {
  override def equals(obj: Any): Boolean = obj match {
    case Player(u, _, _) => u == user
    case _               => false
  }

  def dto: PlayerDto = PlayerDto(user.dto, buyIn, ready)
}
