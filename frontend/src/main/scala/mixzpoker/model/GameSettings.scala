package mixzpoker.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

sealed trait GameSettings {

}

object GameSettings {
  case class PokerSettings(
    playersCount: Int, maxPlayers: Int = 9, minPlayers: Int = 2,
    smallBlind: Int, bigBlind: Int, ante: Int,
    buyInMin: Int, buyInMax: Int
  ) extends GameSettings

  implicit val pokerSettingsEncoder: Encoder[PokerSettings] = deriveEncoder
  implicit val pokerSettingsDecoder: Decoder[PokerSettings] = deriveDecoder
}
