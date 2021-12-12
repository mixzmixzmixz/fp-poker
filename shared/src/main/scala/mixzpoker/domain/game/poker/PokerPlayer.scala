package mixzpoker.domain.game.poker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token
import mixzpoker.domain.game.core.Hand
import mixzpoker.domain.user.{UserId, UserName}


case class PokerPlayer(
  userId: UserId,
  name: UserName,
  seat: Int,
  tokens: Token,
  hand: Hand,
  state: PokerPlayerState
)


object PokerPlayer {
  def fromUser(userId: UserId,name: UserName, buyIn: Token, seat: Int): PokerPlayer =
    PokerPlayer(
      userId = userId,
      name = name,
      seat = seat,
      tokens = buyIn,
      hand = Hand.empty,
      state = PokerPlayerState.Joined
    )


  implicit val pokerPlayerEncoder: Encoder[PokerPlayer] = deriveEncoder
  implicit val pokerPlayerDecoder: Decoder[PokerPlayer] = deriveDecoder
}
