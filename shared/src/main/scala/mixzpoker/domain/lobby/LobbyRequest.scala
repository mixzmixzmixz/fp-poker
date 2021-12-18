package mixzpoker.domain.lobby

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import mixzpoker.domain.game.GameType

sealed trait LobbyRequest

object LobbyRequest {
  @JsonCodec
  case class CreateGameResponse(id: String)

  final case class CreateLobbyRequest(name: String, gameType: GameType) extends LobbyRequest


  implicit val createLobbyRequestDecoder: Decoder[CreateLobbyRequest] = deriveDecoder
  implicit val createLobbyRequestEncoder: Encoder[CreateLobbyRequest] = deriveEncoder
}
