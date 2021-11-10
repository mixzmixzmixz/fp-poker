package mixzpoker.game.core

import io.circe.generic.JsonCodec

@JsonCodec
case class Card(rank: Rank, suit: Suit)
