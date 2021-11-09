package mixzpoker.game.poker.game

import mixzpoker.game.GameId
import mixzpoker.game.core.Card
import mixzpoker.game.core.deck.Deck
import mixzpoker.game.poker.PokerError._
import mixzpoker.game.poker.PokerSettings
import mixzpoker.domain.Token
import mixzpoker.user.UserId


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

  def create(gameId: GameId, settings: PokerSettings, users: List[(UserId, Token)]): ErrOr[PokerGame] = for {
    //todo create players from users
    table <- PokerTable.fromPlayers(???, size = settings.playersCount)
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
