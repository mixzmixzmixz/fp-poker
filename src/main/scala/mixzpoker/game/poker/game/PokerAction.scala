package mixzpoker.game.poker.game

import mixzpoker.domain.Token


sealed trait PokerAction
object PokerAction {
  case object Fold extends PokerAction

  case object Check extends PokerAction

  case class Call(amount: Token) extends PokerAction

  case class Raise(amount: Token) extends PokerAction

  case class AllIn(amount: Token) extends PokerAction
}
