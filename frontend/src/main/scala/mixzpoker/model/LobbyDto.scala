package mixzpoker.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.model.GameSettings.PokerSettings

object LobbyDto {
  case class User(name: String)

  case class Lobby(
    name: String, owner: User, users: List[(User, Int)], gameType: GameType, gameSettings: PokerSettings
  )
  implicit val decodeLobby: Decoder[Lobby] = deriveDecoder
  implicit val encodeLobby: Encoder[Lobby] = deriveEncoder


  case class CreateLobbyRequest(name: String, gameType: GameType)

  implicit val createLobbyRequestDecoder: Decoder[CreateLobbyRequest] = deriveDecoder
  implicit val createLobbyRequestEncoder: Encoder[CreateLobbyRequest] = deriveEncoder

  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder
}
