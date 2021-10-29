package mixzpoker.lobby

import mixzpoker.game.GameError
import mixzpoker.AppError

sealed trait LobbyError extends AppError

object LobbyError {
  type ErrOr[A] = Either[LobbyError, A]

  case object NoSuchLobby extends LobbyError
  case object LobbyAlreadyExist extends LobbyError
  case object InvalidSettings extends LobbyError
  case object UserAlreadyInTheLobby extends LobbyError
  case object NoSuchUser extends LobbyError
  case object UserIsNotOwner extends LobbyError
  case object LobbyFull extends LobbyError
  case object BuyInTooSmall extends LobbyError
  case object BuyInTooLarge extends LobbyError
  case class CreateGameError(gameError: GameError) extends LobbyError
}
