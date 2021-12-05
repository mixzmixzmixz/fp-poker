package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}
import mixzpoker.domain.Token
import mixzpoker.domain.user.UserId


sealed trait PokerEvent

object PokerEvent {
  case class Join(userId: UserId, buyIn: Token) extends PokerEvent
  case class Leave(userId: UserId) extends PokerEvent

  case class Fold(userId: UserId) extends PokerEvent
  case class Check(userId: UserId) extends PokerEvent
  case class Call(userId: UserId, amount: Token) extends PokerEvent
  case class Raise(userId: UserId, amount: Token) extends PokerEvent
  case class AllIn(userId: UserId, amount: Token) extends PokerEvent

  case object RoundStarts extends PokerEvent
  case object PreFlop extends PokerEvent
  case object Flop extends PokerEvent
  case object Turn extends PokerEvent
  case object River extends PokerEvent


  implicit val pokerEventEncoder: Encoder[PokerEvent] = {
    case Fold(userId) => ???
    case Check(userId) => ???
    case Call(userId, amount) => ???
    case Raise(userId, amount) => ???
    case AllIn(userId, amount) => ???
    case RoundStarts => ???
    case PreFlop => ???
    case Flop => ???
    case Turn => ???
    case River => ???
  }

  implicit val pokerEventDecoder: Decoder[PokerEvent] = ???
}
