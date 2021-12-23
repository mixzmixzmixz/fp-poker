package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, KeyDecoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.{Card, Deck, Hand}
import mixzpoker.domain.game.poker.PokerError._
import mixzpoker.domain.game.poker.PokerGameState._
import mixzpoker.domain.user.{UserId, UserName}


final case class PokerGame(
  id: GameId,
  dealerSeat: Int,
  playerToActSeat: Int,
  players: Map[UserId, PokerPlayer],
  deck: Deck,
  pot: Pot,
  board: List[Card],
  state: PokerGameState,
  settings: PokerSettings,
  showdown: Option[Showdown] = None,
  winnersMoney: List[(PokerPlayer, Token)] = List.empty
) {
  def nthAfter(n: Int, seat: Int = dealerSeat): Int = {
    val playersLs = players.values.toList.sortBy(_.seat)
    val (before, after) = playersLs.splitAt(playersLs.indexOf(playersLs.find(_.seat == seat).get))
    val ring = after:::before
    ring(if (n < ring.length) n else n % ring.length).seat
  }

  def smallBlindSeat: Int = nthAfter(1)
  def bigBlindSeat: Int = nthAfter(2)

  def updatePlayer(player: PokerPlayer): PokerGame =
    copy(players = (players - player.userId).updated(player.userId, player))

  def firstEmptySeat: Option[Int] =
    (0 until settings.maxPlayers).filterNot(seat => players.values.map(_.seat).toSet.contains(seat)).headOption

  def playerBySeat(seat: Int): Option[PokerPlayer] =
    players.values.find(_.seat == seat)

  def playerToAct: PokerPlayer =
    players.values.find(_.seat == playerToActSeat).get //todo this should always be valid,

  def dealCards(): PokerGame = {
    val (newDeck, plsWithCards) = players.values.foldLeft((deck, List.empty[PokerPlayer])) { case ((d, pls), p) =>
      val (cards, newDeck) = d.getFirstNCards(2).get //should be ok in poker game
      (newDeck, p.copy(hand = p.hand.addCards(cards))::pls)
    }
    copy(deck = newDeck, players = plsWithCards.map(p => (p.userId, p)).toMap)
  }

  def doesPlayerBetEnough(player: PokerPlayer): Boolean =
    pot.playerBetsThisRound.getOrElse(player.userId, 0) >= toCall

  def toCall: Token =
    pot.playerBetsThisRound.values.max

  def toCallPlayer(player: PokerPlayer): Token =
    toCall - pot.playerBetsThisRound.getOrElse(player.userId, 0)

  def canPlayerFold(userId: UserId): Boolean =
    players.get(userId).exists { player =>
      playerToActSeat == player.seat && player.hasCards
    }

  def canPlayerCheck(userId: UserId): Boolean =
    players.get(userId).exists { player =>
      playerToActSeat == player.seat && player.hasCards && doesPlayerBetEnough(player)
    }

  def canPlayerCall(userId: UserId): Boolean =
    players.get(userId).exists { player =>
      playerToActSeat == player.seat && player.hasCards && !doesPlayerBetEnough(player)
    }

  def canPlayerRaise(userId: UserId, amount: Token): Boolean = {
    // raise should be more than toCall + minBet (usually bigBlind)
    players.get(userId).exists { player =>
      playerToActSeat == player.seat && player.hasCards && amount >= pot.minBet + toCallPlayer(player)
    }
  }

  def canPlayerAllIn(userId: UserId): Boolean =
    players.get(userId).map { player =>
      playerToActSeat == player.seat && player.hasCards
    }.fold(false)(t => t)

  def activePlayers: List[PokerPlayer] =
    players.values.filter(_.hasCards).toList

  def nextToAct(updatedPot: Pot = pot): PokerGame =
    copy(playerToActSeat = nthAfter(1, playerToActSeat), pot = updatedPot)

  def nextState: PokerGameState = state match {
    case RoundStart => if (activePlayers.size > 1) Flop else RoundEnd
    case Flop       => if (activePlayers.size > 1) Turn else RoundEnd
    case Turn       => if (activePlayers.size > 1) River else RoundEnd
    case River      => RoundEnd
    case RoundEnd   => RoundStart
  }

  def flop(card1: Card, card2: Card, card3: Card, newDeck: Deck): PokerGame =
    copy(
      board = card1 :: card2 :: card3 :: Nil,
      deck = newDeck,
      playerToActSeat = nthAfter(1),
      state = Flop,
      pot = pot.nextState(settings.bigBlind)
    )

  def turn(card: Card, newDeck: Deck): PokerGame =
    copy(
      board = board :+ card,
      deck = newDeck,
      playerToActSeat = nthAfter(1),
      state = Turn,
      pot = pot.nextState(settings.bigBlind)
    )

  def river(card: Card, newDeck: Deck): PokerGame =
    copy(
      board = board :+ card,
      deck = newDeck,
      playerToActSeat = nthAfter(1),
      state = River,
      pot = pot.nextState(settings.bigBlind)
    )

  def isRoundFinished: Boolean =
    players.values.filter(_.hasCards).filterNot(_.isAllIned).forall { p =>
      pot.playerBetsThisRound.getOrElse(p.userId, 0) == pot.betToCall
    }

  def checkPlayerCanJoin(userId: UserId, buyIn: Token): Either[PokerError, Unit] = for {
    _ <- Either.cond(!players.contains(userId), (), UserAlreadyInGame)
    _ <- Either.cond(players.size + 1 <= settings.maxPlayers, (), TooManyPlayers)
    _ <- Either.cond(buyIn >= settings.buyInMin, (), BuyInTooLow)
    _ <- Either.cond(buyIn <= settings.buyInMax, (), BuyInTooHigh)
    _ <- firstEmptySeat.toRight[PokerError](NoEmptySeat)
  } yield ()

  def playerJoin(userId: UserId, buyIn: Token, name: UserName): Option[PokerGame] =
    firstEmptySeat.map { seat =>
      copy(players = players.updated(userId, PokerPlayer.fromUser(userId, name, buyIn, seat)))
    }

  def playerLeave(userId: UserId): PokerGame =
    copy(players = players - userId)

  def playerFolds(userId: UserId): Option[PokerGame] =
    players.get(userId).map { player => updatePlayer(player.fold()).nextToAct() }

  def playerChecks(userId: UserId): Option[PokerGame] =
    players.get(userId).map { player => updatePlayer(player.check()).nextToAct() }

  def playerCalls(userId: UserId): Option[PokerGame] = for {
    player <- players.get(userId)
    amount =  toCallPlayer(player)
    player <- player.decreaseBalance(amount).toOption
    newPot =  pot.makeBet(userId, amount)
  } yield updatePlayer(player.call()).nextToAct(newPot)

  def playerRaises(userId: UserId, amount: Token): Option[PokerGame] = for {
    player <- players.get(userId)
    player <- player.decreaseBalance(amount).toOption
    newPot =  pot.makeBet(userId, amount)
  } yield updatePlayer(player.raise()).nextToAct(newPot)

  def playerGoesAllIn(userId: UserId): Option[PokerGame] = for {
    player <- players.get(userId)
    player <- player.decreaseBalance(player.tokens).toOption
    newPot =  pot.makeBet(userId, player.tokens)
  } yield updatePlayer(player.allIn()).nextToAct(newPot)

  def roundEnds(): PokerGame = {

    val moneyWon = pot.playerBets.values.sum
    val (winners, maybeShowdown) = if (activePlayers.size > 1) {
      val showdown = PokerCombinationSolver.sortHands(board, activePlayers)
      (showdown.combs.head.map(_._2), Some(showdown))
    } else {
      (List(activePlayers.head), None)
    }
    val moneyPerWinner = moneyWon / winners.size
    val modulo = moneyWon % winners.size // add to the first player
    val winnersMoney = winners.zipWithIndex.map { case (p, i) =>
      if (i == 0) (p, moneyPerWinner)
      else (p, moneyPerWinner + modulo)
    }
    val updatedPlayers = winnersMoney.map { case (player, money) => player.increaseBalance(money) }

    copy(
      players = updatedPlayers.foldLeft(players) { case (map, p) =>
        (map - p.userId).updated(p.userId, p)
      },
      winnersMoney = winnersMoney,
      showdown = maybeShowdown,
      state = RoundEnd
    )
  }

  def nextRound(shuffledDeck: Deck): PokerGame =
    copy(
      dealerSeat = nthAfter(1),
      playerToActSeat = nthAfter(2),
      board = List.empty,
      state = RoundStart,
      deck = shuffledDeck,
      pot = Pot.empty(minBet = settings.bigBlind, playerIds = players.keys.toList),
      players = players.view.mapValues(_.copy(hand = Hand.empty, state = PokerPlayerState.Joined)).toMap,
      winnersMoney = List.empty,
      showdown = None
    ).dealCards()
}

object PokerGame {

  def create(gameId: GameId, settings: PokerSettings, players: List[(UserId, UserName,  Token)]): PokerGame = {
    val _players = players.zipWithIndex.map { case ((uid, name, buyIn), i) =>
      PokerPlayer.fromUser(uid, name, buyIn, i)
    }
    PokerGame(
      id = gameId,
      dealerSeat = 0,
      playerToActSeat = 1,
      players = Map.from(_players.map(p => p.userId -> p)),
      deck = Deck.of52,
      pot = Pot.empty(minBet = settings.bigBlind, playerIds = players.map(_._1)),
      board = Nil,
      state = RoundStart,
      settings = settings
    )
  }

  def empty(gameId: GameId): PokerGame = {
    PokerGame(
      id = gameId,
      dealerSeat = 0,
      playerToActSeat = 1,
      players = Map.empty,
      deck = Deck.of52,
      pot = Pot.empty(playerIds = Nil),
      board = Nil,
      state = RoundStart,
      settings = PokerSettings.defaults
    )
  }

  implicit val mapUserIdPokerPlayerEncoder: Encoder[Map[UserId, PokerPlayer]] =
    (map: Map[UserId, PokerPlayer]) =>
      Json.obj(map.map { case (id, player) => id.toString -> player.asJson }.toList: _*)

  implicit val keyDecoderUserId: KeyDecoder[UserId] = (key: String) => UserId.fromString(key)
  implicit val mapUserIdPokerPlayerDecoder: Decoder[Map[UserId, PokerPlayer]] = Decoder.decodeMap[UserId, PokerPlayer]


  implicit val pokerGameEncoder: Encoder[PokerGame] = deriveEncoder
  implicit val pokerGameDecoder: Decoder[PokerGame] = deriveDecoder
}

