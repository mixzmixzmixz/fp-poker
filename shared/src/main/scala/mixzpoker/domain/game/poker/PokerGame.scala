package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, KeyDecoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.{Card, Deck}
import mixzpoker.domain.user.{UserId, UserName}


case class PokerGame(
  id: GameId,
  dealerSeat: Int,
  playerToActSeat: Int,
  players: Map[UserId, PokerPlayer],
  deck: Deck,
  pot: Pot,
  board: List[Card],
  state: PokerGameState,
  settings: PokerSettings
) {

  def smallBlindSeat: Int = dealerSeat + 1
  def bigBlindSeat: Int = dealerSeat + 2

  def updatePlayer(player: PokerPlayer): PokerGame =
    copy(players = (players - player.userId).updated(player.userId, player))

  def firstEmptySeat: Option[Int] =
    (0 until settings.maxPlayers).filterNot(seat => players.values.map(_.seat).toSet.contains(seat)).headOption

}

object PokerGame {

  def create(gameId: GameId, settings: PokerSettings, players: List[(UserId, UserName,  Token)]): PokerGame = {
    val _players = players.zipWithIndex.map { case ((uid, name, buyIn), i) =>
      PokerPlayer.fromUser(uid, name, buyIn, i)
    }
    PokerGame(
      id = gameId,
      dealerSeat = 0,
      playerToActSeat = 3 % _players.size,
      players = Map.from(_players.map(p => p.userId -> p)),
      deck = Deck.of52,
      pot = Pot.empty(minBet = settings.bigBlind),
      board = Nil,
      state = PokerGameState.RoundStart,
      settings = settings
    )
  }

  def empty(gameId: GameId): PokerGame = {
    PokerGame(
      id = gameId,
      dealerSeat = 0,
      playerToActSeat = 0,
      players = Map.empty,
      deck = Deck.of52,
      pot = Pot.empty(),
      board = Nil,
      state = PokerGameState.RoundStart,
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

