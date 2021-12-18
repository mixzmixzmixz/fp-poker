package mixzpoker.domain.lobby

import mixzpoker.domain.AppError
import mixzpoker.domain.game.GameError

sealed trait LobbyError extends AppError

object LobbyError {
  final case object NoSuchLobby extends LobbyError
  final case object LobbyAlreadyExist extends LobbyError
  final case object InvalidSettings extends LobbyError
  final case object UserAlreadyInTheLobby extends LobbyError
  final case object NoSuchUser extends LobbyError
  final case object UserIsNotOwner extends LobbyError
  final case object LobbyFull extends LobbyError
  final case object BuyInTooSmall extends LobbyError
  final case object BuyInTooLarge extends LobbyError
  final case object GameIsAlreadyStarted extends LobbyError
  final case class CreateGameError(gameError: GameError) extends LobbyError
}
