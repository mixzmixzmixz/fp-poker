package mixzpoker.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.model.GameSettings.PokerSettings

object LobbyDto {
  case class User(name: String)
  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder

  case class Player(user: User, buyIn: Int)
  implicit val playerDecoder: Decoder[Player] = deriveDecoder
  implicit val playerEncoder: Encoder[Player] = deriveEncoder


  case class Lobby(
    name: String, owner: User, users: List[Player], gameType: GameType, gameSettings: PokerSettings
  )
  implicit val decodeLobby: Decoder[Lobby] = deriveDecoder
  implicit val encodeLobby: Encoder[Lobby] = deriveEncoder

  case class CreateLobbyRequest(name: String, gameType: GameType)
  implicit val createLobbyRequestDecoder: Decoder[CreateLobbyRequest] = deriveDecoder
  implicit val createLobbyRequestEncoder: Encoder[CreateLobbyRequest] = deriveEncoder

  case class JoinLobbyRequest(buyIn: Int)
  implicit val joinLobbyRequestDecoder: Decoder[JoinLobbyRequest] = deriveDecoder
  implicit val joinLobbyRequestEncoder: Encoder[JoinLobbyRequest] = deriveEncoder
}
