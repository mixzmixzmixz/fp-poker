package mixzpoker.game

import mixzpoker.AppError
import mixzpoker.infrastructure.broker.BrokerError

trait GameError extends AppError

object GameError {
  type ErrOr[A] = Either[GameError, A]

  case object NoSuchGame extends GameError
  case object InvalidGameIdFormat extends GameError
  case object WrongGameType extends GameError
  case class GameBrokerError(ve: BrokerError) extends GameError
  case class DecodeError(err: io.circe.Error) extends GameError
}
