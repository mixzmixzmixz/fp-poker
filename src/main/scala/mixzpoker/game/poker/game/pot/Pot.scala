package mixzpoker.game.poker.game.pot


import cats.data.EitherT
import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import mixzpoker.domain.Token
import mixzpoker.game.poker.game.PokerGameError
import mixzpoker.game.poker.player.PokerPlayer
import mixzpoker.user.UserId
import mixzpoker.game.poker.game.PokerGameError._


trait Pot[F[_]] {
  def minBet: F[Token]
  def maxBet: F[Token]
  def canCheck: F[ErrOr[Unit]]

  def playerCall(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]]
  def playerRaise(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]]
  def playerAllIn(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]]
}

object Pot {
  def empty[F[_]: Sync](minBet: Token = 0, maxBet: Token = 0): F[Pot[F]] = for {
    minBetRef <- Ref.of(minBet)
    maxBetRef <- Ref.of(maxBet)
    betToCallRef <- Ref.of[F, Token](0)
    playerBetsThisRoundRef <- Ref.of[F, Map[UserId, Token]](Map.empty)
    playerBetsRef <- Ref.of[F, Map[UserId, Token]](Map.empty)
  } yield new Pot[F] {

    private def checkPlayerCallEnough(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]] = for {
      betToCall <- betToCallRef.get
      playerBetsThisRound <- playerBetsThisRoundRef.get
      toCall = betToCall - playerBetsThisRound.getOrElse(player.getUser.id, 0)
      res = if (amount < toCall)
        Left(NotEnoughTokensToCall)
      else if (amount > toCall)
        Left(MoreTokensThanNeededToCall)
      else
        Right(())
    } yield res

    private def checkPlayerRaiseEnough(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]] = for {
      betToCall <- betToCallRef.get
      playerBetsThisRound <- playerBetsThisRoundRef.get
      toCall = betToCall - playerBetsThisRound.getOrElse(player.getUser.id, 0)
      mb <- minBetRef.get
      minRaise = toCall + mb
      res = if (amount < minRaise)
        Left(NotEnoughTokensToRaise)
      else
        Right(())
    } yield res

    override def minBet: F[Token] = minBetRef.get

    override def maxBet: F[Token] = maxBetRef.get

    override def canCheck: F[ErrOr[Unit]] = for {
      betToCall <- betToCallRef.get
      res = if (betToCall != 0) Left(CanNotCheck) else Right(())
    } yield res


    override def playerCall(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]] = (for {
      _ <- EitherT[F, PokerGameError, Unit](player.checkBalance(amount))
      _ <- EitherT(checkPlayerCallEnough(player, amount))
      _ <- EitherT(player.decreaseBalance(amount))
      playerBetsThisRound <- EitherT.right(playerBetsThisRoundRef.get)
      id = player.getUser.id
      playerBet = playerBetsThisRound.getOrElse(id, 0) + amount
      _ <- EitherT.right[PokerGameError](playerBetsThisRoundRef.update(_.updated(id, playerBet)))
      playerBets <- EitherT.right[PokerGameError](playerBetsRef.get).map(_.getOrElse(id, 0))
      _ <- EitherT.right[PokerGameError](playerBetsRef.update(_.updated(id, playerBet + playerBets)))
    } yield ()).value

    override def playerRaise(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]] = {
      for {
        _ <- EitherT(player.checkBalance(amount))
        _ <- EitherT(checkPlayerRaiseEnough(player, amount))
        _ <- EitherT(player.decreaseBalance(amount))
        id = player.getUser.id
        playerBetsThisRound <- EitherT.right(playerBetsThisRoundRef.get)
        playerBet = playerBetsThisRound.getOrElse(id, 0) + amount
        playerBets <- EitherT.right[PokerGameError](playerBetsRef.get.map(_.getOrElse(id, 0)))
        _ <- EitherT.right[PokerGameError](playerBetsRef.update(_.updated(id, playerBet + playerBets)))
        _ <- EitherT.right[PokerGameError](betToCallRef.update(_ => playerBet))
        _ <- EitherT.right[PokerGameError](playerBetsThisRoundRef.update(_.updated(id, playerBet)))
      } yield ()
    }.value

    override def playerAllIn(player: PokerPlayer[F], amount: Token): F[ErrOr[Unit]] = {
      for {
        _ <- EitherT(player.checkBalanceEquals(amount))
        _ <- EitherT(player.decreaseBalance(amount))
        id = player.getUser.id
        playerBetsThisRound <- EitherT.right(playerBetsThisRoundRef.get)
        playerBets <- EitherT.right(playerBetsRef.get.map(_.getOrElse(id, 0)))
        playerBet = playerBetsThisRound.getOrElse(id, 0) + amount
        _ <- EitherT.right[PokerGameError](playerBetsThisRoundRef.update(_.updated(id, playerBet)))
        _ <- EitherT.right[PokerGameError](playerBetsRef.update(_.updated(id, playerBet + playerBets)))
        _ <- EitherT.right[PokerGameError](betToCallRef.update(x => if (playerBet > x) playerBet else x))
      } yield ()
    }.value
  }

}
