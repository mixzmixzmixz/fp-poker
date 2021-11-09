package mixzpoker.game.poker.player


sealed trait PokerPlayerState

object PokerPlayerState {
  case object JoinedPlayer extends PokerPlayerState
  case object FoldedPlayer extends PokerPlayerState
  case object ActivePlayer extends PokerPlayerState
}