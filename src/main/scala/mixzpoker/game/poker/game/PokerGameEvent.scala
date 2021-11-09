package mixzpoker.game.poker.game

import io.circe.{Encoder, Json}
import mixzpoker.domain.Token
import mixzpoker.user.UserId

sealed trait PokerGameEvent

object PokerGameEvent {
  case class PlayerJoins(userId: UserId, buyIn: Token) extends PokerGameEvent
  case class PlayerLeaves(userId: UserId) extends PokerGameEvent

  case class PlayerFold(userId: UserId) extends PokerGameEvent
  case class PlayerCheck(userId: UserId) extends PokerGameEvent
  case class PlayerCall(userId: UserId, amount: Token) extends PokerGameEvent
  case class PlayerRaise(userId: UserId, amount: Token) extends PokerGameEvent
  case class PlayerAllIn(userId: UserId, amount: Token) extends PokerGameEvent

  case object RoundStarts extends PokerGameEvent
  case object PreFlop extends PokerGameEvent
  case object Flop extends PokerGameEvent
  case object Turn extends PokerGameEvent
  case object River extends PokerGameEvent


  implicit val pokerEventEncoder: Encoder[PokerGameEvent] = (a: PokerGameEvent) => a match {
    case PlayerFold(userId) => ???
    case PlayerCheck(userId) => ???
    case PlayerCall(userId, amount) => ???
    case PlayerRaise(userId, amount) => ???
    case PlayerAllIn(userId, amount) => ???
    case RoundStarts => ???
    case PreFlop => ???
    case Flop => ???
    case Turn => ???
    case River => ???
  }
}