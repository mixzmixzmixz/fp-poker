package mixzpoker.game.poker

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import tofu.logging.Logging
import tofu.syntax.logging._

import mixzpoker.AppError
import mixzpoker.domain.Token
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.game.poker._
import mixzpoker.domain.game.poker.PokerEvent._
import mixzpoker.domain.game.poker.PokerOutputMessage._
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.Hand
import mixzpoker.domain.user.{UserId, UserName}
import mixzpoker.game.poker.PokerError._
import mixzpoker.lobby.Player


// controls getGame's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def getGame: F[PokerGame]
  def topic: Topic[F, PokerOutputMessage]
  def chatTopic: Topic[F, ChatOutputMessage]
  def id: GameId
  def processEvent(ec: PokerEventContext): F[PokerOutputMessage]
}

object PokerGameManager {
  def create[F[_]: Concurrent: Logging](
    gameId: GameId, settings: PokerSettings, players: List[Player], queue: Queue[F, PokerEventContext]
  ): F[PokerGameManager[F]] =
    for {
      gameRef    <- Ref.of[F, PokerGame](PokerGame.create(
                                        gameId, settings,
                                        players.map(p => (p.user.id, p.user.name, p.buyIn))
                                      ))
      g          <- gameRef.get
      _topic     <- Topic[F, PokerOutputMessage](PokerOutputMessage.GameState(g))
      _chatTopic <- Topic[F, ChatOutputMessage](ChatOutputMessage.KeepAlive)
    } yield new PokerGameManager[F] {

      val secondsForAction: Int = 30 //todo move to settings

      override def id: GameId = gameId
      override def topic: Topic[F, PokerOutputMessage] = _topic
      override def chatTopic: Topic[F, ChatOutputMessage] = _chatTopic
      override def getGame: F[PokerGame] = gameRef.get

      override def processEvent(ec: PokerEventContext): F[PokerOutputMessage] = {
        for {
          game <- gameRef.get
          game <- (ec.event, ec.userId) match {
                    case (event: PokerGameEvent, None)        => processGameEvent(game, event)
                    case (event: PokerPlayerEvent, Some(uid)) => processPlayerEvent(game, event, uid)
                  }
          _    <- gameRef.update(_ => game)
        } yield PokerOutputMessage.GameState(game = game): PokerOutputMessage
      }.recover {
        case err: AppError => PokerOutputMessage.ErrorMessage(err.toString): PokerOutputMessage
      }.handleErrorWith(err =>
        error"got some err: ${err.getMessage}" *>
        error"stacktrace: ${err.getStackTrace.mkString("\n")}" *>
          (PokerOutputMessage.ErrorMessage(err.toString): PokerOutputMessage).pure[F]
      )

      def processPlayerEvent(game: PokerGame, event: PokerPlayerEvent, userId: UserId): F[PokerGame] = {
        event match {
          case Ping              => Right(game)
          case Join(buyIn, name) => playerJoin(game, userId, name, buyIn)
          case Leave             => playerLeave(game, userId)
          case Fold              => playerFold(game, userId)
          case Check             => playerCheck(game, userId)
          case Call(amount)      => playerCall(game, userId, amount)
          case Raise(amount)     => playerRaise(game, userId, amount)
          case AllIn             => playerAllIn(game, userId)
        }
      }.liftTo[F]

      def processGameEvent(game: PokerGame, event: PokerGameEvent): F[PokerGame] = {
        event match {
          case RoundStarts =>
            for {
              g <- roundStarts(game).liftTo[F]
              _ <- _topic.publish1(RoundStart(1))
              p <- g.playerBySeat(g.playerToActSeat).toRight(NoSuchPlayer).liftTo[F]
              _ <- _topic.publish1(PlayerToAction(p.userId, secondsForAction)) //todo timer
            } yield g
          case PreFlop     => ???
          case Flop        => ???
          case Turn        => ???
          case River       => ???
        }
      }

      def ensurePlayerCalledEnough(pot: Pot, player: PokerPlayer, amount: Token): Either[PokerError, Unit] = {
        val toCall = pot.betToCall - pot.playerBetsThisRound.getOrElse(player.userId, 0)
        if (amount < toCall)
          Left(NotEnoughTokensToCall: PokerError)
        else if (amount > toCall)
          Left(MoreTokensThanNeededToCall: PokerError)
        else
          Right(())
      }

      def ensurePlayerRaisedEnough(pot: Pot, player: PokerPlayer, amount: Token): Either[PokerError, Unit] = {
        val toCall = pot.betToCall - pot.playerBetsThisRound.getOrElse(player.userId, 0)
        val minRaise = pot.minBet + toCall
        Either.cond(amount >= minRaise, (), NotEnoughTokensToRaise: PokerError)
      }

      def decreaseBalance(player: PokerPlayer, delta: Token): Either[PokerError, PokerPlayer] =
        Either.cond(delta <= player.tokens, player.copy(tokens = player.tokens - delta), UserDoesNotHaveEnoughTokens)

      def increaseBalance(player: PokerPlayer, delta: Token): Either[PokerError, PokerPlayer] =
        Right(player.copy(tokens = player.tokens + delta))

      def ensureBalanceEquals(player: PokerPlayer, amount: Token): Either[PokerError, Unit] =
        Either.cond(player.tokens == amount, (), UserBalanceError(s"balance not equal $amount"))

      //todo lock user's balance here
      def playerJoin(game: PokerGame, userId: UserId, name: UserName, buyIn: Token): Either[PokerError, PokerGame] = for {
        _    <- Either.cond(game.players.size + 1 <= game.settings.maxPlayers, (), TooManyPlayers)
        _    <- Either.cond(buyIn >= game.settings.buyInMin, (), BuyInTooLow)
        _    <- Either.cond(buyIn <= game.settings.buyInMax, (), BuyInTooHigh)
        seat <- game.firstEmptySeat.toRight[PokerError](NoEmptySeat)
        p    = PokerPlayer.fromUser(userId, name, buyIn, seat)
      } yield game.copy(players = game.players.updated(userId, p))

      //todo update user's balance here?
      def playerLeave(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        _ <- Either.cond(game.players.contains(userId), (), NoSuchPlayer)
      } yield game.copy(players = game.players - userId)

      def playerToAct(game: PokerGame, userId: UserId): Either[PokerError, PokerPlayer] = for {
        p <- game.players.get(userId).toRight[PokerError](WrongUserId)
        _ <- Either.cond(p.seat == game.playerToActSeat, (), NoPlayerOnSeat(game.playerToActSeat))
      } yield p

      def playerFold(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- playerToAct(game, userId)
      } yield game.updatePlayer(player.copy(hand = Hand.empty, state = PokerPlayerState.Folded))

      def playerCheck(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- playerToAct(game, userId)
        _      <- Either.cond(
                    game.pot.betToCall == game.pot.playerBetsThisRound.getOrElse(player.userId, 0),
                    (),
                    CanNotCheck
                  )
        //todo check logic
      } yield game

      def playerCall(game: PokerGame, userId: UserId, amount: Token): Either[PokerError, PokerGame] = for {
        player <- playerToAct(game, userId)
        _      <- ensurePlayerCalledEnough(game.pot, player, amount)
        player <- decreaseBalance(player, amount)
        pBet   =  game.pot.playerBetsThisRound.getOrElse(player.userId, 0) + amount
        pBets  =  game.pot.playerBets.getOrElse(player.userId, 0) + pBet
        pot    =  game.pot.copy(
                    playerBetsThisRound = game.pot.playerBetsThisRound.updated(player.userId, pBet),
                    playerBets = game.pot.playerBets.updated(player.userId, pBets)
                  )
      } yield game.updatePlayer(player).copy(pot = pot)

      def playerRaise(game: PokerGame, userId: UserId, amount: Token): Either[PokerError, PokerGame] = for {
        player <- playerToAct(game, userId)
        _      <- ensurePlayerRaisedEnough(game.pot, player, amount)
        player <- decreaseBalance(player, amount)
        pBet   =  game.pot.playerBetsThisRound.getOrElse(player.userId, 0) + amount
        pBets  =  game.pot.playerBets.getOrElse(player.userId, 0) + pBet
        pot    =  game.pot.copy(
                    playerBetsThisRound = game.pot.playerBetsThisRound.updated(player.userId, pBet),
                    playerBets = game.pot.playerBets.updated(player.userId, pBets),
                    betToCall = pBet
                  )
      } yield game.updatePlayer(player).copy(pot = pot)

      def playerAllIn(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- playerToAct(game, userId)
        _      <- Either.cond(player.tokens > 0, (), UserBalanceError(s"Balance is zero!"))
        player <- decreaseBalance(player, player.tokens)
        pBet   =  game.pot.playerBetsThisRound.getOrElse(player.userId, 0) + player.tokens
        pBets  =  game.pot.playerBets.getOrElse(player.userId, 0) + pBet
        pot    =  game.pot.copy(
                    playerBetsThisRound = game.pot.playerBetsThisRound.updated(player.userId, pBet),
                    playerBets = game.pot.playerBets.updated(player.userId, pBets),
                    betToCall = if (pBet > game.pot.betToCall) pBet else game.pot.betToCall
                  )
      } yield game.updatePlayer(player).copy(pot = pot)

      def betBlind(game: PokerGame, player: PokerPlayer, blind: Token): Either[PokerError, PokerGame] = for {
        p   <- decreaseBalance(player, blind)
        pot =  game.pot.copy(
                  playerBetsThisRound = game.pot.playerBetsThisRound.updated(p.userId, blind),
                  playerBets = game.pot.playerBets.updated(p.userId, blind),
                  betToCall = blind
                )
      } yield game.updatePlayer(p).copy(pot = pot, playerToActSeat = game.nthAfter(1, seat = game.playerToActSeat))

      def roundStarts(_game: PokerGame): Either[PokerError, PokerGame] = {
        val game = _game.nextRound()
        for {
          sbPlayer <- game.playerBySeat(game.smallBlindSeat).toRight(NoSuchPlayer)
          game     <- betBlind(game, sbPlayer, Math.min(sbPlayer.tokens, game.settings.smallBlind))
          bbPlayer <- game.playerBySeat(game.bigBlindSeat).toRight(NoSuchPlayer)
          game     <- betBlind(game, bbPlayer, Math.min(bbPlayer.tokens, game.settings.bigBlind))
        } yield game
      }

    }

}
