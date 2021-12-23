package mixzpoker.game.poker

import mixzpoker.domain.Token
import mixzpoker.domain.user.{UserId, UserName}


sealed trait PokerCommand

object PokerCommand {
  final case class PingCommand(userId: UserId) extends PokerCommand
  final case class JoinCommand(userId: UserId, buyIn: Token, name: UserName) extends PokerCommand
  final case class LeaveCommand(userId: UserId) extends PokerCommand

  final case class FoldCommand(userId: UserId) extends PokerCommand
  final case class CheckCommand(userId: UserId) extends PokerCommand
  final case class CallCommand(userId: UserId) extends PokerCommand
  final case class RaiseCommand(userId: UserId, amount: Token) extends PokerCommand
  final case class AllInCommand(userId: UserId) extends PokerCommand
}

