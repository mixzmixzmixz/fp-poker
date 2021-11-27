package mixzpoker.lobby

import mixzpoker.user.User
import mixzpoker.domain.Token
import mixzpoker.domain.game.{GameSettings, GameType}
import mixzpoker.domain.lobby.LobbyDto.{LobbyDto, PlayerDto}
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

  def dto: LobbyDto = LobbyDto(
    name = name.value,
    owner = owner.dto,
    users = users.map { case (user, buyIn) => PlayerDto(user.dto, buyIn) },
    gameType = gameType,
    gameSettings = gameSettings
  )
}

