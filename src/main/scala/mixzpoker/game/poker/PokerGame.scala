package mixzpoker.game.poker

import cats.effect.Sync
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import mixzpoker.game.{Game, GameId}
import mixzpoker.game.core.Card
import mixzpoker.game.core.deck.Deck
import mixzpoker.game.poker.game.pot.Pot
import mixzpoker.game.poker.game.PokerAction._
import mixzpoker.game.poker.game.{PokerAction, PokerGameState, PokerTable}
import mixzpoker.game.poker.PokerError._
import mixzpoker.game.poker.player.PokerPlayerState._
import mixzpoker.game.poker.player.PokerPlayer
import mixzpoker.domain.Token
import mixzpoker.user.User


trait PokerGame[F[_]] extends Game[F] {
  def table: PokerTable
  def deck: Deck
  def pot: Pot[F]
  def cards: List[Card]
  def gameState: PokerGameState
  def settings: PokerSettings
}

/*
  So, the idea here is to hide interaction with GameState behind the message broker (live - Kafka, test - queue)
  As a result, we should not worry about synchronization and transaction since only one message processed at a time

 */

object PokerGame {
  //todo as wrapper over refs
  case class PokerGameBase(
    id: GameId,
    table: PokerTable, deck: Deck, pot: Pot, cards: List[Card], gameState: PokerGameState, settings: PokerSettings
  ) extends PokerGame {
    def playerFold(player: PokerPlayer): ErrOr[PokerGame] = for {
      _ <- table.getPlayerToActState(player)
      newTable <- table.updatePlayer(FoldedPlayer(player))
    } yield copy(table = newTable)

    def playerCheck(player: PokerPlayer): ErrOr[PokerGame] = for {
      _ <- table.getPlayerToActState(player)
      _ <- pot.canCheck
    } yield this

    //todo reduce code duplication here in bets

    def playerCall(player: PokerPlayer, amount: Token): ErrOr[PokerGame] = for {
      activePlayer <- table.getPlayerToActState(player)
      pp <- pot.playerCall(player, amount)
      (_player, _pot) = pp
      newTable <- table.updatePlayer(ActivePlayer(_player, activePlayer.hand))
    } yield copy(table = newTable, pot = _pot)

    def playerRaise(player: PokerPlayer, amount: Token): ErrOr[PokerGame] = for {
      activePlayer <- table.getPlayerToActState(player)
      pp <- pot.playerRaise(player, amount)
      (_player, _pot) = pp
      newTable <- table.updatePlayer(ActivePlayer(_player, activePlayer.hand))
    } yield copy(table = newTable, pot = _pot)

    def playerAllIn(player: PokerPlayer, amount: Token): ErrOr[PokerGame] = for {
      activePlayer <- table.getPlayerToActState(player)
      pp <- pot.playerAllIn(player, amount)
      (_player, _pot) = pp
      newTable <- table.updatePlayer(ActivePlayer(_player, activePlayer.hand))
    } yield copy(table = newTable, pot = _pot)

    def playerAct(player: PokerPlayer, action: PokerAction): ErrOr[PokerGame] = gameState match {
      case PokerGameState.RoundStart => Left(SomeError("To early to act"))
      case _ => action match {
        case Fold => playerFold(player)
        case Check => playerCheck(player)
        case Call(amount) => playerCall(player, amount)
        case Raise(amount) => playerRaise(player, amount)
        case AllIn(amount) => playerAllIn(player, amount)
      } // todo check whether round continues right after
    }

    def nextAction(): ErrOr[PokerGame] = {
      // move turn, check if bets needed, notify next player, deal cards
      ???
    }
  }


  def fromUsers[F[_]: Sync](users: List[(User, Token)], settings: PokerSettings): ErrOr[PokerGame[F]] = for {
    table <- PokerTable.fromPlayers(users.map {case (user, token) => PokerPlayer.fromUser(user)}, settings.playersCount)
    deck = Deck.of52
    pot <- Pot.empty[F](minBet = settings.bigBlind)
  } yield PokerGameBase(
    id = GameId.fromRandom,
    table, deck, pot, List(), PokerGameState.RoundStart, settings
  )

  implicit val encoderPokerGameBase: Encoder[PokerGameBase] = deriveEncoder

  implicit val encoderPokerGame: Encoder[PokerGame] = Encoder.instance {
    case t: PokerGameBase => t.asJson
    case _ =>Json.fromString("err")
  }
}
