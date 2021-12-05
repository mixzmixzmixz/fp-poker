package mixzpoker.game.poker.game

import mixzpoker.game.core.Card
import mixzpoker.game.core.deck.Deck
import mixzpoker.game.poker.PokerError._
import mixzpoker.domain.Token
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.domain.user.UserId
import mixzpoker.game.poker.player.PokerPlayer
import mixzpoker.lobby.Player


case class PokerGame(
  id: GameId,
  table: PokerTable,
  deck: Deck,
  pot: Pot,
  board: List[Card],
  state: PokerGameState,
  settings: PokerSettings
) {

  def playerFold(userId: UserId): ErrOr[Unit] = for {
    p <- table.playerToAct(userId)
    newTable <- table.updatePlayer(p.fold())
  } yield copy(table = newTable)

  def playerCheck(userId: UserId): ErrOr[Unit] = for {
    p <- table.playerToAct(userId)
    _ <- pot.canCheck(p)
  } yield()

  def playerCall(userId: UserId, amount: Token): ErrOr[Unit] = for {
    p1 <- table.playerToAct(userId)
    pp <- pot.playerCall(p1, amount)
    (_pot, _player) = pp
    newTable <- table.updatePlayer(_player)
  } yield copy(table = newTable, pot = _pot)

  def playerRaise(userId: UserId, amount: Token): ErrOr[Unit] = for {
    p1 <- table.playerToAct(userId)
    pp <- pot.playerRaise(p1, amount)
    (_pot, _player) = pp
    newTable <- table.updatePlayer(_player)
  } yield copy(table = newTable, pot = _pot)

  def playerAllIn(userId: UserId, amount: Token): ErrOr[Unit] = for {
    p1 <- table.playerToAct(userId)
    pp <- pot.playerAllIn(p1, amount)
    (_pot, _player) = pp
    newTable <- table.updatePlayer(_player)
  } yield copy(table = newTable, pot = _pot)

  def nextAction(): ErrOr[Unit] = ???

}


object PokerGame {

  def create(gameId: GameId, settings: PokerSettings, players: List[Player]): ErrOr[PokerGame] = {
    val _players = players.zipWithIndex.map { case (p, i) => PokerPlayer.fromUser(p.user.id, p.buyIn, i) }
    for {
      table <- PokerTable.fromPlayers(_players, size = settings.playersCount)
      game = PokerGame(
        id = gameId,
        table = table,
        deck = Deck.of52,
        pot = Pot.empty(minBet = settings.bigBlind),
        board = Nil,
        state = PokerGameState.RoundStart,
        settings = settings
      )
    } yield game
  }
}
