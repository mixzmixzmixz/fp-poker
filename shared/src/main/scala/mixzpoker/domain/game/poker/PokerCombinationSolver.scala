package mixzpoker.domain.game.poker

import mixzpoker.domain.game.core.Card

object PokerCombinationSolver {

  def combinations(board: List[Card], players: List[PokerPlayer]): List[(PokerCombination, PokerPlayer)] =
    players.map { player =>
      val comb = (board ++ player.hand.cards)
        .combinations(5)
        .map(PokerCombination.apply)
        .maxBy(_.score)
      (comb, player)
    }

  def sortHands(board: List[Card], players: List[PokerPlayer]): Showdown =
    Showdown(combinations(board, players).groupBy(_._1.score).toList.sortBy(_._1).map(_._2))


}
