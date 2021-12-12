package mixzpoker.domain.game.poker

import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, KeyDecoder}
import mixzpoker.domain.Token
import mixzpoker.domain.user.UserId


case class Pot(
  minBet: Token, // min bet allowed (e.g. big blind)
  maxBet: Token, // max bet allowed (e.g. pot limit in limit-holdem)
  betToCall: Token, // last bet made needed to be called by others
  playerBetsThisRound: Map[UserId, Token], // Bets made by players during this round
  playerBets: Map[UserId, Token], // all bets made by players
)

object Pot {
  def empty(minBet: Token = 0, maxBet: Token = 0): Pot =
    Pot(
      minBet = minBet, maxBet = maxBet,
      betToCall = 0,
      playerBetsThisRound = Map.empty,
      playerBets = Map.empty
    )


  implicit val mapUserIdTokenEncoder: Encoder[Map[UserId, Token]] =
    (map: Map[UserId, Token]) =>
      Json.obj(map.map { case (id, token) => id.toString -> token.asJson }.toList: _*)

  implicit val keyDecoderUserId: KeyDecoder[UserId] = (key: String) => UserId.fromString(key)
  implicit val mapUserIdTokenDecoder: Decoder[Map[UserId, Token]] = Decoder.decodeMap[UserId, Token]

  implicit val potEncoder: Encoder[Pot] = deriveEncoder
  implicit val potDecoder: Decoder[Pot] = deriveDecoder
}
