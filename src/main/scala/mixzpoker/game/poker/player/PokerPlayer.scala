package mixzpoker.game.poker.player

import cats.data.EitherT
import cats.implicits._
import cats.effect.Sync
import cats.effect.concurrent.Ref

import mixzpoker.domain.Token
import mixzpoker.game.poker.game.PokerGameError
import mixzpoker.game.poker.game.PokerGameError._
import mixzpoker.user.User


trait PokerPlayer[F[_]] {
  def getUser: User
  def getTokens: F[Token]

  def checkBalance(amount: Token): F[ErrOr[Unit]]
  def checkBalanceEquals(amount: Token): F[ErrOr[Unit]]
  def decreaseBalance(delta: Token): F[ErrOr[Unit]]
  def increaseBalance(delta: Token): F[ErrOr[Unit]]
}


object PokerPlayer {
  def fromUser[F[_]: Sync](user: User): F[PokerPlayer[F]] = for {
    tokens <- Ref.of(user.amount)
  } yield new PokerPlayer[F] {
    override def getUser: User = user

    override def getTokens: F[Token] = tokens.get

    override def checkBalance(amount: Token): F[ErrOr[Unit]] = for {
      t <- getTokens
    } yield Either.cond(amount <= t, (), UserDoesNotHaveEnoughTokens)

    override def checkBalanceEquals(amount: Token): F[ErrOr[Unit]] = for {
      t <- getTokens
    } yield Either.cond(amount == t, (), UserBalanceError(s"balance not equal $amount"))

    override def decreaseBalance(delta: Token): F[ErrOr[Unit]] = (for {
      t <- EitherT.right[PokerGameError](getTokens)
      _ <- EitherT.cond[F](t - delta >= 0, (), UserDoesNotHaveEnoughTokens)
      _ <- EitherT.right[PokerGameError](tokens.update(_ - delta))
    } yield ()).value

    override def increaseBalance(delta: Token): F[ErrOr[Unit]] =
      tokens.update(_ + delta).map(_ => ().asRight[PokerGameError])
  }

}
