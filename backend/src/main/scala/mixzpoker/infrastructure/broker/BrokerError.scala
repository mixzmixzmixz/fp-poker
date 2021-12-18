package mixzpoker.infrastructure.broker

import mixzpoker.domain.AppError

sealed trait BrokerError extends AppError

object BrokerError {
  type ErrOr[A] = Either[BrokerError, A]

  final case object NoSuchTopic extends BrokerError
  final case object TopicAlreadyExist extends BrokerError
}
