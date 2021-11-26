package mixzpoker.lobby

import io.circe.syntax._
import io.circe.{Encoder, Json}
import mixzpoker.game.{GameSettings, GameType}
import mixzpoker.user.User
import mixzpoker.domain.Token
import LobbyError._


case class Lobby(
  name: LobbyName,
  owner: User,
  users: List[(User, Token)] = List(),
  gameType: GameType,
  gameSettings: GameSettings,
) {
  def size: Int = users.size

  def checkUserIsOwner(user: User): ErrOr[Unit] = Either.cond(user == owner, (), UserIsNotOwner)

  def joinPlayer(user: User, buyIn: Token): ErrOr[Lobby] =
    if (users.contains(user))
      Left(UserAlreadyInTheLobby)
    else if (size >= gameSettings.maxPlayers)
      Left(LobbyFull)
    else if (buyIn < gameSettings.buyInMin)
      Left(BuyInTooSmall)
    else if ( buyIn > gameSettings.buyInMax)
      Left(BuyInTooLarge)
    else
      Right(copy(users = (user, buyIn)::users))

  def leavePlayer(user: User): ErrOr[Lobby] =
    Either.cond(users.contains(user), copy(users = users.filterNot(_._1 == user)), NoSuchUser)

}

object Lobby {
  implicit val fooEncoder: Encoder[Lobby] = (a: Lobby) => Json.obj(
    "name" -> Json.fromString(a.name.value),
    "owner" -> a.owner.asJson,
    "users" -> Json.arr(a.users.map {
      case (user, token) => Json.obj("user" -> user.asJson, "buyIn" -> Json.fromInt(token))
    }: _*),
    "gameType" -> a.gameType.asJson,
    "gameSettings" -> a.gameSettings.asJson
  )
}
