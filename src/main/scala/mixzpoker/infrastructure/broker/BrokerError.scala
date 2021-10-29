package mixzpoker.infrastructure.broker

trait BrokerError

object BrokerError {
  type ErrOr[A] = Either[BrokerError, A]

  case object NoSuchTopic extends BrokerError
  case object TopicAlreadyExist extends BrokerError
}
