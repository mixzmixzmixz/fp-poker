package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, KeyDecoder}
import mixzpoker.domain.Token
import mixzpoker.domain.user.UserId


//todo rename round
final case class Pot(
  minBet: Token, // min bet allowed (e.g. big blind)
  maxBet: Token, // max bet allowed (e.g. pot limit in limit-holdem) // todo use maxBet in some versions of the game
  betToCall: Token, // last bet made needed to be called by others
  playerBetsThisRound: Map[UserId, Token], // Bets made by players during this round (rounds are preflop, flop, etc)
  playerBets: Map[UserId, Token], // all bets made by players
) {
  def makeBet(userId: UserId, bet: Token): Pot = {
    val pBet = playerBetsThisRound.getOrElse(userId, 0) + bet
    val pBets = playerBets.getOrElse(userId, 0) + pBet
    copy(
      playerBetsThisRound = playerBetsThisRound.updated(userId, pBet),
//      playerBets = playerBets.updated(userId, pBets),  //todo update overall bets in the end of each round
      betToCall = if (pBet > betToCall) pBet else betToCall
    )
  }

  def nextState(minBet: Token): Pot = {
    copy(
      playerBets = playerBets.toList.map { case (id, tokens) =>
        (id, tokens + playerBetsThisRound.getOrElse(id, 0))
      }.toMap,
      playerBetsThisRound = playerBetsThisRound.view.mapValues(_ => 0).toMap,
      betToCall = 0,
      minBet = minBet
    )
  }
}

object Pot {
  def empty(minBet: Token = 0, maxBet: Token = 0, playerIds: List[UserId]): Pot =
    Pot(
      minBet = minBet, maxBet = maxBet,
      betToCall = 0,
      playerBetsThisRound = playerIds.map(id => (id, 0)).toMap,
      playerBets = playerIds.map(id => (id, 0)).toMap
    )


  implicit val mapUserIdTokenEncoder: Encoder[Map[UserId, Token]] =
    (map: Map[UserId, Token]) =>
      Json.obj(map.map { case (id, token) => id.toString -> token.asJson }.toList: _*)

  implicit val keyDecoderUserId: KeyDecoder[UserId] = (key: String) => UserId.fromString(key)
  implicit val mapUserIdTokenDecoder: Decoder[Map[UserId, Token]] = Decoder.decodeMap[UserId, Token]

  implicit val potEncoder: Encoder[Pot] = deriveEncoder
  implicit val potDecoder: Decoder[Pot] = deriveDecoder
}
