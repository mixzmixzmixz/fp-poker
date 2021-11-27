package mixzpoker.domain.lobby

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import mixzpoker.domain.game.{GameSettings, GameType}
import mixzpoker.domain.User.UserDto.UserDto

object LobbyDto {
  @JsonCodec
  case class CreateGameResponse(id: String)

  case class PlayerDto(user: UserDto, buyIn: Int)

  case class CreateLobbyRequest(name: String, gameType: GameType)

  case class LobbyDto(
    name: String, owner: UserDto, users: List[PlayerDto], gameType: GameType, gameSettings: GameSettings
  )

  implicit val playerDecoder: Decoder[PlayerDto] = deriveDecoder
  implicit val playerEncoder: Encoder[PlayerDto] = deriveEncoder

  implicit val lobbyDtoDecoder: Decoder[LobbyDto] = deriveDecoder
  implicit val lobbyDtoEncoder: Encoder[LobbyDto] = deriveEncoder

  implicit val createLobbyRequestDecoder: Decoder[CreateLobbyRequest] = deriveDecoder
  implicit val createLobbyRequestEncoder: Encoder[CreateLobbyRequest] = deriveEncoder
}
