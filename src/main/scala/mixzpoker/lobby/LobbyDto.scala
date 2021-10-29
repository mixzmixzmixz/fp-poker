package mixzpoker.lobby

import io.circe.generic.JsonCodec

import mixzpoker.domain.Token
import mixzpoker.game.GameType


object LobbyDto {

  @JsonCodec
  case class CreateLobbyRequest(name: String, gameType: GameType)

  @JsonCodec
  case class JoinLobbyRequest(buyIn: Token)

  @JsonCodec
  case class CreateGameResponse(id: String)


}
