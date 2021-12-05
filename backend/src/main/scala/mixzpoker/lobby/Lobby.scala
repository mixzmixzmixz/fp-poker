package mixzpoker.lobby

import mixzpoker.user.User
import mixzpoker.domain.Token
import mixzpoker.domain.game.{GameId, GameSettings, GameType}
import mixzpoker.domain.lobby.LobbyDto.LobbyDto
import LobbyError._


case class Lobby(
  name: LobbyName,
  owner: User,
  players: List[Player] = List(),
  gameType: GameType,
  gameSettings: GameSettings,
  gameId: Option[GameId] = None
) {
  def size: Int = players.size

  def checkUserIsOwner(user: User): ErrOr[Unit] = Either.cond(user == owner, (), UserIsNotOwner)

  def joinPlayer(user: User, buyIn: Token): ErrOr[Lobby] =
    if (players.map(_.user).contains(user))   Left(UserAlreadyInTheLobby)
    else if (size >= gameSettings.maxPlayers) Left(LobbyFull)
    else if (buyIn < gameSettings.buyInMin)   Left(BuyInTooSmall)
    else if (buyIn > gameSettings.buyInMax)   Left(BuyInTooLarge)
    else                                      Right(copy(players = Player(user, buyIn)::players))

  def leavePlayer(user: User): ErrOr[Lobby] =
    Either.cond(
      players.map(_.user).contains(user),
      copy(players = players.filterNot(_.user == user)),
      NoSuchUser
    )

  def updatePlayerReadiness(user: User, readiness: Boolean): Either[LobbyError, Lobby] = for {
    player <- players.find(_.user == user).toRight[LobbyError](NoSuchUser)
  } yield copy(players = (players.toSet - player + player.copy(ready = readiness)).toList)

  def startGame(gameId: GameId): Either[LobbyError, Lobby] = this.gameId match {
    case Some(_) => Left(GameIsAlreadyStarted)
    case None => Right(copy(gameId = Some(gameId)))
  }

  def satisfiesSettings: Boolean = {
    (players.length >= gameSettings.minPlayers) &&
      players.length <= gameSettings.maxPlayers &&
      players.forall(p => p.buyIn >= gameSettings.buyInMin && p.buyIn <= gameSettings.buyInMax)
  }

  def isStarted: Boolean = gameId.isDefined

  def dto: LobbyDto = LobbyDto(
    name = name.value,
    owner = owner.dto,
    players = players.map(_.dto),
    gameType = gameType,
    gameSettings = gameSettings,
    gameId = gameId.map(_.toString)
  )
}

