package mixzpoker.domain.game

import mixzpoker.domain.AppError

trait GameError extends AppError

object GameError {
  final case object NoSuchGame extends GameError
  final case object GameAlreadyExist extends GameError
  final case object InvalidGameIdFormat extends GameError
  final case object WrongGameType extends GameError
  final case object WrongSettingsType extends GameError
  final case class DecodeError(err: io.circe.Error) extends GameError
}
