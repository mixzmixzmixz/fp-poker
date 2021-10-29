package mixzpoker.lobby

import io.circe.syntax._
import io.circe.{Encoder, Json}

import mixzpoker.game.{GameSettings, GameType}
import mixzpoker.game.poker.PokerSettings
import mixzpoker.user.User
import mixzpoker.domain.Token
import LobbyError._



sealed trait Lobby {
  def name: LobbyName
  def owner: User
  def users: List[(User, Token)]
  def gameType: GameType
  def gameSettings: GameSettings

  def size: Int = users.size

  def checkUserIsOwner(user: User): ErrOr[Unit]

  def joinPlayer(user: User, buyIn: Token): ErrOr[Lobby]
  def leavePlayer(user: User): ErrOr[Lobby]

}

object Lobby {

  case class DefaultLobby(
    name: LobbyName, owner: User, users: List[(User, Token)],
    gameType: GameType, gameSettings: GameSettings
  ) extends Lobby {

    override def joinPlayer(user: User, buyIn: Token): ErrOr[Lobby] =
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

    override def leavePlayer(user: User): ErrOr[Lobby] =
      Either.cond(users.contains(user), copy(users = users.filterNot(_._1 == user)), NoSuchUser)

    override def checkUserIsOwner(user: User): ErrOr[Unit] =
      Either.cond(user == owner, (), UserIsNotOwner)

  }


  def of[F[_]](name: String, owner: User, gameType: GameType): ErrOr[Lobby] = for {
    lobbyName <- LobbyName.fromString(name)
    settings <- (gameType match {
      case GameType.Poker => PokerSettings.create()
    }).toRight(InvalidSettings)
  } yield DefaultLobby(lobbyName, owner, List(), gameType, settings)

  implicit val fooEncoder: Encoder[DefaultLobby] = (a: DefaultLobby) => Json.obj(
    "name" -> Json.fromString(a.name.value),
    "owner" -> a.owner.asJson,
    "users" -> Json.arr(a.users.map {
      case (user, token) => Json.obj("user" -> user.asJson, "buyIn" -> Json.fromInt(token))
    }: _*),
    "gameType" -> a.gameType.asJson,
    "gameSettings" -> a.gameSettings.asJson
  )

  implicit val encodeLobby: Encoder[Lobby] = Encoder.instance {
    case l: DefaultLobby => l.asJson
  }
}
