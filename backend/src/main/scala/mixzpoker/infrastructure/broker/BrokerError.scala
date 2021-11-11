package mixzpoker.infrastructure.broker

import mixzpoker.AppError

sealed trait BrokerError extends AppError

object BrokerError {
  type ErrOr[A] = Either[BrokerError, A]

  case object NoSuchTopic extends BrokerError
  case object TopicAlreadyExist extends BrokerError
}
