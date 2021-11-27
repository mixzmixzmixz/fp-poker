package mixzpoker.domain.game.poker


import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.game.GameSettings


case class PokerSettings(
  playersCount: Int, maxPlayers: Int = 9, minPlayers: Int = 2,
  smallBlind: Int, bigBlind: Int, ante: Int,
  buyInMin: Int, buyInMax: Int
) extends GameSettings


object PokerSettings {
  def create(
    playersCount: Int = 2, maxPlayers: Int = 9, minPlayers: Int = 2,
    smallBlind: Int = 1, bigBlind: Int = 2, ante: Int = 0,
    buyInMin: Int = 0, buyInMax: Int = Int.MaxValue
  ): Option[PokerSettings] = {
    //todo to validated
    if (playersCount < 2 || playersCount > 9
      || smallBlind < 1 || bigBlind != smallBlind * 2
      || buyInMin < 0 || buyInMax < buyInMin
    )
      None
    else
      Some(PokerSettings(
        playersCount, maxPlayers, minPlayers,
        smallBlind, bigBlind, ante,
        buyInMin, buyInMax
      ))
  }

  implicit val pokerSettingsEncoder: Encoder[PokerSettings] = deriveEncoder
  implicit val pokerSettingsDecoder: Decoder[PokerSettings] = deriveDecoder
}
