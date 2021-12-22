package mixzpoker.domain.lobby

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import mixzpoker.domain.game.{GameId, GameType}

sealed trait LobbyRequest

object LobbyRequest {
  @JsonCodec
  final case class CreateGameResponse(id: GameId)

  final case class CreateLobbyRequest(name: LobbyName, gameType: GameType) extends LobbyRequest


  implicit val createLobbyRequestDecoder: Decoder[CreateLobbyRequest] = deriveDecoder
  implicit val createLobbyRequestEncoder: Encoder[CreateLobbyRequest] = deriveEncoder
}
