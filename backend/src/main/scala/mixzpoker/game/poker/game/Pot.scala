package mixzpoker.game.poker.game

import io.circe.{Encoder, Json}
import mixzpoker.domain.Token
import mixzpoker.game.poker.PokerError
import mixzpoker.game.poker.PokerError._
import mixzpoker.game.poker.player.PokerPlayer
import mixzpoker.user.UserId


case class Pot(
  minBet: Token, // min bet allowed (e.g. big blind)
  maxBet: Token, // max bet allowed (e.g. pot limit in limit-holdem)
  betToCall: Token, // last bet made needed to be called by others
  playerBetsThisRound: Map[UserId, Token], // Bets made by players during this round
  playerBets: Map[UserId, Token], // all bets made by players
) {
  def canCheck(player: PokerPlayer): ErrOr[Unit] =
    Either.cond(betToCall == playerBetsThisRound.getOrElse(player.userId, 0), (), CanNotCheck)

  private def checkPlayerCalledEnough(player: PokerPlayer, amount: Token): ErrOr[Unit] = {
    val toCall = betToCall - playerBetsThisRound.getOrElse(player.userId, 0)
    if (amount < toCall)
      Left(NotEnoughTokensToCall: PokerError)
    else if (amount > toCall)
      Left(MoreTokensThanNeededToCall: PokerError)
    else
      Right(())
  }

  private def checkPlayerRaisedEnough(player: PokerPlayer, amount: Token): ErrOr[Unit] = {
    val toCall = betToCall - playerBetsThisRound.getOrElse(player.userId, 0)
    val minRaise = minBet + toCall
    Either.cond(amount >= minRaise, (), NotEnoughTokensToRaise: PokerError)
  }

  def playerCall(player: PokerPlayer, amount: Token): ErrOr[(Pot, PokerPlayer)] = for {
    _ <- checkPlayerCalledEnough(player, amount)
    p <- player.call(amount)
    pBet = playerBetsThisRound.getOrElse(player.userId, 0) + amount
    pBets = playerBets.getOrElse(player.userId, 0) + pBet
    pot = copy(
      playerBetsThisRound = playerBetsThisRound.updated(player.userId, pBet),
      playerBets = playerBets.updated(player.userId, pBets)
    )
  } yield (pot, p)

  def playerRaise(player: PokerPlayer, amount: Token): ErrOr[(Pot, PokerPlayer)] = for {
    _ <- checkPlayerRaisedEnough(player, amount)
    p <- player.raise(amount)
    pBet = playerBetsThisRound.getOrElse(player.userId, 0) + amount
    pBets = playerBets.getOrElse(player.userId, 0) + pBet
    pot = copy(
      playerBetsThisRound = playerBetsThisRound.updated(player.userId, pBet),
      playerBets = playerBets.updated(player.userId, pBets),
      betToCall = pBet
    )
  } yield (pot, p)

  def playerAllIn(player: PokerPlayer, amount: Token): ErrOr[(Pot, PokerPlayer)] = for {
    _ <- player.checkBalanceEquals(amount)
    p <- player.allIn(amount)
    pBet = playerBetsThisRound.getOrElse(player.userId, 0) + amount
    pBets = playerBets.getOrElse(player.userId, 0) + pBet
    pot = copy(
      playerBetsThisRound = playerBetsThisRound.updated(player.userId, pBet),
      playerBets = playerBets.updated(player.userId, pBets),
      betToCall = if (pBet > betToCall) pBet else betToCall
    )
  } yield (pot, p)
}

object Pot {
  def empty(minBet: Token = 0, maxBet: Token = 0): Pot =
    Pot(
      minBet = minBet, maxBet = maxBet,
      betToCall = 0,
      playerBetsThisRound = Map.empty,
      playerBets = Map.empty
    )

  implicit val potEncoder: Encoder[Pot] = (a: Pot) => Json.obj(
    "betToCall" -> Json.fromInt(a.betToCall),
    "total" -> Json.fromInt(a.playerBets.values.sum)
  )
}
