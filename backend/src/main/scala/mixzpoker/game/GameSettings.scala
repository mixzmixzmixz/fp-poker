package mixzpoker.game

import io.circe.{Encoder, Json}
import io.circe.syntax._
import mixzpoker.game.poker.PokerSettings


trait GameSettings {
  def maxPlayers: Int
  def minPlayers: Int

  def buyInMin: Int
  def buyInMax: Int
}

object GameSettings {
  implicit val encodeGameType: Encoder[GameSettings] = Encoder.instance {
    case settings: PokerSettings => settings.asJson
    case _ => Json.fromString("wrong settings")
  }
}