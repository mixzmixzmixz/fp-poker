package mixzpoker.game.poker.game

sealed trait PokerGameState

object PokerGameState {
  case object RoundStart extends PokerGameState
  case object PreFlop extends PokerGameState
  case object Flop extends PokerGameState
  case object Turn extends PokerGameState
  case object River extends PokerGameState
}