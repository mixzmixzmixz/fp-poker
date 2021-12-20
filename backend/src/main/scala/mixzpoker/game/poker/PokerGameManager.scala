package mixzpoker.game.poker

import cats.effect.{Concurrent, Timer}
import cats.effect.concurrent.Ref
import cats.effect.syntax.all._
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import tofu.generate.GenUUID
import tofu.logging.Logging
import tofu.syntax.logging._

import scala.concurrent.duration._
import scala.util.Random
import mixzpoker.domain.{AppError, Token}
import mixzpoker.domain.chat.ChatOutputMessage
import mixzpoker.domain.game.core.Deck
import mixzpoker.domain.game.poker._
import mixzpoker.domain.game.poker.PokerEvent._
import mixzpoker.domain.game.poker.PokerGameState._
import mixzpoker.domain.game.poker.PokerOutputMessage._
import mixzpoker.domain.game.{GameEventId, GameId}
import mixzpoker.domain.lobby.Player
import mixzpoker.domain.user.{UserId, UserName}
import mixzpoker.domain.game.poker.PokerError._


// controls getGame's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def getGame: F[PokerGame]
  def topic: Topic[F, PokerOutputMessage]
  def chatTopic: Topic[F, ChatOutputMessage]
  def id: GameId
  def processEvent(ec: PokerEventContext): F[PokerOutputMessage]
}

object PokerGameManager {
  def create[F[_]: Concurrent: Logging: Timer: GenUUID](
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
                    case (event: PokerPlayerEvent, Some(uid)) => for {
                      game <- processPlayerEvent(game, event, uid)
                      eid  <- GenUUID[F].randomUUID.map(GameEventId.fromUUID)
                      _    <- if (game.isRoundFinished)
                                queue.enqueue1(PokerEventContext(eid, ec.gameId, None, NextState(game.nextState)))
                              else
                                game.playerBySeat(game.playerToActSeat)
                                  .toRight(NoSuchPlayer)
                                  .liftTo[F]
                                  .flatMap { p =>
                                    _topic.publish1(PlayerToAction(p.userId, secondsForAction))
                                  }
                    } yield game
                  }

          // todo _ <- gameRef.modify()
          _    <- gameRef.update(_ => game)
        } yield PokerOutputMessage.GameState(game = game): PokerOutputMessage
      }.recover {
        case err: AppError => PokerOutputMessage.ErrorMessage(ec.userId, err.toString): PokerOutputMessage
      }.handleErrorWith(err =>
        error"got some err: ${err.getMessage}" *>
        error"stacktrace: ${err.getStackTrace.mkString("\n")}" *>
          (PokerOutputMessage.ErrorMessage(ec.userId, err.toString): PokerOutputMessage).pure[F]
      )

      //todo change to f[either]
      def processPlayerEvent(game: PokerGame, event: PokerPlayerEvent, userId: UserId): F[PokerGame] = {
        event match {
          case Ping              => Right(game)
          case Join(buyIn, name) => playerJoin(game, userId, name, buyIn)
          case Leave             => playerLeave(game, userId)
          case Fold              => playerFold(game, userId)
          case Check             => playerCheck(game, userId)
          case Call              => playerCall(game, userId)
          case Raise(amount)     => playerRaise(game, userId, amount)
          case AllIn             => playerAllIn(game, userId)
        }
      }.liftTo[F]

      def processGameEvent(game: PokerGame, event: PokerGameEvent): F[PokerGame] = {
        event match {
          case NextState(PokerGameState.RoundStart) =>
            for {
              _ <- _topic.publish1(PokerOutputMessage.LogMessage("Next round begins in 5s"))
              _ <- Timer[F].sleep(5.seconds)
              shuffledDeck <- Concurrent[F].delay { Random.shuffle(Deck.cards52) }.map(Deck.ofCards52)
              g <- roundStarts(game, shuffledDeck).liftTo[F]
              _ <- _topic.publish1(PokerOutputMessage.LogMessage("Next round has begun"))
              p <- g.playerBySeat(g.playerToActSeat).toRight(NoSuchPlayer).liftTo[F]
              _ <- _topic.publish1(PlayerToAction(p.userId, secondsForAction)) //todo timer
            } yield g

          case NextState(RoundEnd) =>
            val moneyWon = game.pot.playerBets.values.sum
            val _game = game.calculateWinners()
            val winners = _game.winners
            val moneyPerWinner = moneyWon / winners.size
            val modulo = moneyWon % winners.size // add to the first player
            val winnersMoney = winners.zipWithIndex.map { case (p, i) =>
              if (i == 0) (p, moneyPerWinner)
              else (p, moneyPerWinner + modulo)
            }
            val updatedPlayers = winnersMoney.traverse { case (p, money) => increaseBalance(p, money)}

            for {
              g   <- updatedPlayers.liftTo[F].map(_game.updatePlayers)
              _   <- _topic.publish1(LogMessage("Showdown!"))
              winnersStr = winnersMoney.map { case (p, m) =>
                              s"${p.name} -> $m"
                            }.mkString(", ")
              _   <- _topic.publish1(LogMessage(s"Winners: $winnersStr"))
              eid <- GenUUID[F].randomUUID.map(GameEventId.fromUUID)
              _   <- queue.enqueue1(PokerEventContext(eid, game.id, None, NextState(game.nextState)))
            } yield g

          case NextState(Flop)        =>
            val g = game.flop()
            for {
              _ <- _topic.publish1(PokerOutputMessage.LogMessage("Flop State"))
              p <- g.playerBySeat(g.playerToActSeat).toRight(NoSuchPlayer).liftTo[F]
              _ <- _topic.publish1(PlayerToAction(p.userId, secondsForAction)) //todo timer
            } yield g

          case NextState(Turn)        =>
            val g = game.turn()
            for {
              _ <- _topic.publish1(PokerOutputMessage.LogMessage("Turn State"))
              p <- g.playerBySeat(g.playerToActSeat).toRight(NoSuchPlayer).liftTo[F]
              _ <- _topic.publish1(PlayerToAction(p.userId, secondsForAction)) //todo timer
            } yield g

          case NextState(River)       =>
            val g = game.river()
            for {
              _ <- _topic.publish1(PokerOutputMessage.LogMessage("River State"))
              p <- g.playerBySeat(g.playerToActSeat).toRight(NoSuchPlayer).liftTo[F]
              _ <- _topic.publish1(PlayerToAction(p.userId, secondsForAction)) //todo timer
            } yield g
        }
      }

      def decreaseBalance(player: PokerPlayer, delta: Token): Either[PokerError, PokerPlayer] =
        Either.cond(delta <= player.tokens, player.copy(tokens = player.tokens - delta), UserDoesNotHaveEnoughTokens)

      def increaseBalance(player: PokerPlayer, delta: Token): Either[PokerError, PokerPlayer] =
        Right(player.copy(tokens = player.tokens + delta))

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

      def playerFold(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- game.players.get(userId).toRight(NoSuchPlayer)
        _      <- Either.cond(game.canPlayerFold(player), (), SomeError("can not fold"))
      } yield game.updatePlayer(player.fold()).nextToAct()

      def playerCheck(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- game.players.get(userId).toRight(NoSuchPlayer)
        _      <- Either.cond(game.canPlayerCheck(player), (), CanNotCheck)
      } yield game.updatePlayer(player.check()).nextToAct()

      def playerCall(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- game.players.get(userId).toRight(NoSuchPlayer)
        _      <- Either.cond(game.canPlayerCall(player), (), SomeError("can not call"))
        amount =  game.toCallPlayer(player)
        player <- decreaseBalance(player, amount)
        pot    =  game.pot.makeBet(userId, amount)
      } yield game.updatePlayer(player.call()).nextToAct(pot)

      def playerRaise(game: PokerGame, userId: UserId, amount: Token): Either[PokerError, PokerGame] = for {
        player <- game.players.get(userId).toRight(NoSuchPlayer)
        _      <- Either.cond(game.canPlayerRaise(player, amount), (), SomeError("can not raise"))
        player <- decreaseBalance(player, amount)
        pot    =  game.pot.makeBet(userId, amount)
      } yield game.updatePlayer(player.raise()).nextToAct(pot)

      def playerAllIn(game: PokerGame, userId: UserId): Either[PokerError, PokerGame] = for {
        player <- game.players.get(userId).toRight(NoSuchPlayer)
        _      <- Either.cond(game.canPlayerAllIn(player), (), SomeError("can not all in"))
        player <- decreaseBalance(player, player.tokens)
        pot    =  game.pot.makeBet(userId, player.tokens)
      } yield game.updatePlayer(player.allIn()).nextToAct(pot)

      def betBlind(game: PokerGame, player: PokerPlayer, blind: Token): Either[PokerError, PokerGame] = for {
        p   <- decreaseBalance(player, blind)
        pot =  game.pot.makeBet(player.userId, blind)
      } yield game.updatePlayer(p).nextToAct(pot)

      def roundStarts(_game: PokerGame, shuffledDeck: Deck): Either[PokerError, PokerGame] = {
        val game = _game.nextRound(shuffledDeck)
        for {
          sbPlayer <- game.playerBySeat(game.smallBlindSeat).toRight(NoSuchPlayer)
          game     <- betBlind(game, sbPlayer, Math.min(sbPlayer.tokens, game.settings.smallBlind))
          bbPlayer <- game.playerBySeat(game.bigBlindSeat).toRight(NoSuchPlayer)
          game     <- betBlind(game, bbPlayer, Math.min(bbPlayer.tokens, game.settings.bigBlind))
        } yield game
      }
    }

}
