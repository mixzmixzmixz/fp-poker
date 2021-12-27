package mixzpoker.game.poker

import cats.effect.{Concurrent, Timer}
import cats.effect.concurrent.Ref
import cats.effect.syntax.all._
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Topic
import tofu.generate.{GenRandom, GenUUID}
import tofu.logging.Logging
import tofu.syntax.logging._
import io.circe.syntax._
import scala.concurrent.duration._
import org.http4s.websocket.WebSocketFrame.Text

import mixzpoker.domain.{AppError, Token}
import mixzpoker.domain.game.core.Deck
import mixzpoker.domain.game.poker._
import mixzpoker.domain.game.poker.PokerEvent._
import mixzpoker.domain.game.poker.PokerOutputMessage._
import mixzpoker.domain.game.{GameEventId, GameId}
import mixzpoker.domain.lobby.Player
import mixzpoker.domain.user.{User, UserId, UserName}
import mixzpoker.domain.game.poker.PokerError._
import mixzpoker.game.poker.PokerCommand._


//todo naming! what is round ?
// controls getGame's flow, player's timeouts and so
trait PokerGameManager[F[_]] {
  def id: GameId
  def getGame: F[PokerGame]

  def handleCommand(command: PokerCommand): F[Unit]
  def publishEvent(event: PokerEvent): F[PokerGame]
  def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): Stream[F, Text]
}

object PokerGameManager {
  type EventsMessages = (List[PokerEvent], List[PokerOutputMessage])

  def create[F[_]: Concurrent: Logging: Timer: GenUUID: GenRandom](
    gameId: GameId, settings: PokerSettings, players: List[Player],
    storeEvent: (PokerEvent, GameId) => F[Unit],
    saveSnapshot: (PokerGame, GameId) => F[Unit]
  ): F[PokerGameManager[F]] = {
    for {
      gameRef <- Ref.of[F, PokerGame](PokerGame.create(
                                        gameId, settings,
                                        players.map(p => (p.user.id, p.user.name, p.buyIn))
                                      ))
      g       <- gameRef.get
      topic   <- Topic[F, PokerOutputMessage](PokerOutputMessage.GameState(g))
    } yield {
      val pm: PokerGameManager[F] = new PokerGameManager[F] {

        val secondsForAction: Int = 30 //todo move to settings

        override def id: GameId = gameId
        override def getGame: F[PokerGame] = gameRef.get

        override def toClient(gameId: GameId, userRef: Ref[F, Option[User]]): Stream[F, Text] =
          topic
            .subscribe(1000)
            .evalFilter {
              case PokerOutputMessage.ErrorMessage(Some(id), _) => userRef.get.map(_.fold(false)(_.id == id))
              case _  => true.pure[F]
            }.map(msg => Text(msg.asJson.noSpaces))

        override def handleCommand(command: PokerCommand): F[Unit] = command match {
          case PingCommand(userId)              => handlePingCommand(userId)
          case JoinCommand(userId, buyIn, name) => handleJoinCommand(userId, buyIn, name)
          case LeaveCommand(userId)             => handleLeaveCommand(userId)
          case FoldCommand(userId)              => handleFoldCommand(userId)
          case CheckCommand(userId)             => handleCheckCommand(userId)
          case CallCommand(userId)              => handleCallCommand(userId)
          case RaiseCommand(userId, amount)     => handleRaiseCommand(userId, amount)
          case AllInCommand(userId)             => handleAllInCommand(userId)
        }

        def handlePingCommand(userId: UserId): F[Unit] =
          gameRef.get.flatMap(g => topic.publish1(GameState(g)))

        //todo lock user's balance here
        def handleJoinCommand(userId: UserId, buyIn: Token, name: UserName): F[Unit] =
          gameRef.get.map(_.checkPlayerCanJoin(userId, buyIn)).flatMap {
            case Left(err) => topic.publish1(PokerOutputMessage.ErrorMessage(userId.some, err.toString))
            case Right(_)  => publishEvent(PlayerJoinedEvent(userId, buyIn, name)) as ()
          }

        //todo update user's balance here?
        def handleLeaveCommand(userId: UserId): F[Unit] =
          gameRef.get.map(_.players.contains(userId)).ifM(
            publishEvent(PlayerFoldedEvent(userId)) *>
              publishEvent(PlayerLeftEvent(userId)) as (),
            ().pure[F]
          )

        def handleFoldCommand(userId: UserId): F[Unit] =
          gameRef.get.map(_.canPlayerFold(userId)).ifM(
            publishEvent(PlayerFoldedEvent(userId)).flatMap(onRoundFinished),
            ().pure[F]
          )

        def handleCheckCommand(userId: UserId): F[Unit] =
          gameRef.get.map(_.canPlayerCheck(userId)).ifM(
            publishEvent(PlayerCheckedEvent(userId)).flatMap(onRoundFinished),
            ().pure[F]
          )

        def handleCallCommand(userId: UserId): F[Unit] =
          gameRef.get.map(_.canPlayerCall(userId)).ifM(
            publishEvent(PlayerCalledEvent(userId)).flatMap(onRoundFinished),
            ().pure[F]
          )

        def handleRaiseCommand(userId: UserId, amount: Token): F[Unit] =
          gameRef.get.map(_.canPlayerRaise(userId, amount)).ifM(
            publishEvent(PlayerRaisedEvent(userId, amount)).flatMap(onRoundFinished),
            ().pure[F]
          )

        def handleAllInCommand(userId: UserId): F[Unit] =
          gameRef.get.map(_.canPlayerAllIn(userId)).ifM(
            publishEvent(PlayerAllInedEvent(userId)).flatMap(onRoundFinished),
            ().pure[F]
          )

        override def publishEvent(event: PokerEvent): F[PokerGame] = {
          // publish event to EventLog(Kafka),
          // update GameState
          // publish updated GameState through the Topic
          // return updated GameState
          for {
            _    <- info"Publish event GameId=${id.toString} event=${event.toString}"
            game <- gameRef.updateAndGet(processEvent(event))
            // todo publish to EventLog
            _    <- storeEvent(event, gameId)
            _    <- topic.publish1(GameState(game))
            _    <- afterEvent(event, game) //goes after game state publishing since it's publishes event as well
          } yield game
        }

        def processEvent(event: PokerEvent)(game: PokerGame): PokerGame = event match {
          case PlayerJoinedEvent(userId, buyIn, name) => game.playerJoin(userId, buyIn, name).getOrElse(game)
          case PlayerLeftEvent(userId)                => game.playerLeave(userId)
          case PlayerFoldedEvent(userId)              => game.playerFolds(userId).getOrElse(game)
          case PlayerCheckedEvent(userId)             => game.playerChecks(userId).getOrElse(game)
          case PlayerCalledEvent(userId)              => game.playerCalls(userId).getOrElse(game)
          case PlayerRaisedEvent(userId, amount)      => game.playerRaises(userId, amount).getOrElse(game)
          case PlayerAllInedEvent(userId)             => game.playerGoesAllIn(userId).getOrElse(game)

          case NewRoundStartedEvent(deck)                  => roundStarts(game, deck).toOption.getOrElse(game)
          case CardsDealtEvent(playersWithCards, deck)     => ???
          case FlopStartedEvent(card1, card2, card3, deck) => game.flop(card1, card2, card3, deck)
          case TurnStartedEvent(card, deck)                => game.turn(card, deck)
          case RiverStartedEvent(card, deck)               => game.river(card, deck)
          case RoundFinishedEvent                          => game.roundEnds()
        }

        // performs some actions after event
        def afterEvent(event: PokerEvent, game: PokerGame): F[Unit] = event match {
          case PlayerJoinedEvent(userId, buyIn, name) =>
            topic.publish1(LogMessage(s"Player $name has joined the table with $buyIn tokens! Keep an eye on your money!"))

          case PlayerLeftEvent(userId) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name has left the table! Hope they have something left in their pockets!"))

          case PlayerFoldedEvent(userId) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name folded!")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case PlayerCheckedEvent(userId) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name checked!")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case PlayerCalledEvent(userId) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name called !")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case PlayerRaisedEvent(userId, amount) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name raised with $amount tokens!")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case PlayerAllInedEvent(userId) =>
            val name = game.players.get(userId).map(_.name.toString).getOrElse("")
            topic.publish1(LogMessage(s"Player $name goes ALL IN. Are They Crazy?!")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case NewRoundStartedEvent(_) =>
            topic.publish1(PokerOutputMessage.LogMessage("Next round begins in 5s"))
              .flatTap(_ => Timer[F].sleep(3.seconds)) *>  //todo run in background
              topic.publish1(LogMessage("Next round has begun")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction)) *>
              saveSnapshot(game, id)

          case CardsDealtEvent(_, _) => ???
          case FlopStartedEvent(card1, card2, card3, _) =>
            topic.publish1(LogMessage(s"Flop! $card1 $card2 $card3")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case TurnStartedEvent(card, _) =>
            topic.publish1(LogMessage(s"Turn! $card")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case RiverStartedEvent(card, _) =>
            topic.publish1(LogMessage(s"River! $card")) *>
              topic.publish1(PlayerToAction(game.playerToAct.userId, secondsForAction))

          case PokerEvent.RoundFinishedEvent =>
            val winnersStr = game.winnersMoney.map { case (p, m) => s"${p.name} -> $m" }.mkString(", ")
            topic.publish1(LogMessage(if (game.showdown.isDefined) "Showdown!" else "No Showdown!")) *>
              topic.publish1(LogMessage(s"Winners: $winnersStr")) *>
              GenRandom
                .nextLong
                .map(Deck.shuffledOf52)
                .map(deck => NewRoundStartedEvent(deck))
                .flatMap(publishEvent)
                .as(())


        }

        def onRoundFinished(game: PokerGame): F[Unit] = {
          //if round is fnished publish event about next round
          if (game.isRoundFinished)
            game.state match {
              case PokerGameState.RoundStart =>
                if (game.activePlayers.size > 1)
                  game.deck.getFirstNCards(3).toRight(EmptyDeck).liftTo[F].flatMap {
                    case (card1::card2::card3::Nil, deck) =>
                      publishEvent(FlopStartedEvent(card1, card2, card3, deck)) as ()
                  }
                else publishEvent(RoundFinishedEvent) as ()

              case PokerGameState.Flop =>
                if (game.activePlayers.size > 1)
                  game.deck.getFirstNCards().toRight(EmptyDeck).liftTo[F].flatMap {
                    case (card::Nil, deck) => publishEvent(TurnStartedEvent(card, deck)) as ()
                  }
                else publishEvent(RoundFinishedEvent) as ()

              case PokerGameState.Turn =>
                if (game.activePlayers.size > 1)
                  game.deck.getFirstNCards().toRight(EmptyDeck).liftTo[F].flatMap {
                    case (card::Nil, deck) => publishEvent(RiverStartedEvent(card, deck)) as ()
                  }
                else publishEvent(RoundFinishedEvent) as ()

              case PokerGameState.River =>
                publishEvent(RoundFinishedEvent) as ()

              case PokerGameState.RoundEnd =>
                warn"onRoundFinished should not be called on RoundEnd"
            }

          else ().pure[F]
        }

        def betBlind(game: PokerGame, player: PokerPlayer, blind: Token): Either[PokerError, PokerGame] = for {
          p   <- player.decreaseBalance(blind)
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
      pm
    }
  }.flatTap { manager =>
    GenRandom
      .nextLong
      .map(Deck.shuffledOf52)
      .map(deck => NewRoundStartedEvent(deck))
      .flatMap(manager.publishEvent)
  }

}
