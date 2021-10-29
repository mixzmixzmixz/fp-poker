package mixzpoker.game.poker.game

import mixzpoker.fsm.Event
import mixzpoker.game.poker.player.PokerPlayer

trait PokerGameEvent extends Event

object PokerGameEvent {
  case object DrawCards extends PokerGameEvent
  case class PlayerAct(player: PokerPlayer, action: PokerAction) extends PokerGameEvent
  case object CalculateHands extends PokerGameEvent
}