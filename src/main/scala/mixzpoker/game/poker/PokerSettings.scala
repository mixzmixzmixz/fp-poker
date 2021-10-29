package mixzpoker.game.poker

import io.circe.Encoder
import io.circe.generic.JsonCodec
import io.circe.syntax._

import mixzpoker.game.GameSettings

sealed trait PokerSettings extends GameSettings {
  def playersCount: Int
  def smallBlind: Int
  def bigBlind: Int
  def ante: Int
}


object PokerSettings {

  @JsonCodec
  case class Settings(
    playersCount: Int, maxPlayers: Int = 9, minPlayers: Int = 2,
    smallBlind: Int, bigBlind: Int, ante: Int,
    buyInMin: Int, buyInMax: Int
  ) extends PokerSettings

  def create(
    playersCount: Int = 2, maxPlayers: Int = 9, minPlayers: Int = 2,
    smallBlind: Int = 1, bigBlind: Int = 2, ante: Int = 0,
    buyInMin: Int = 0, buyInMax: Int = Int.MaxValue
  ): Option[Settings] =
    if (playersCount < 2 || playersCount > 9
      || smallBlind < 1 || bigBlind != smallBlind * 2
      || buyInMin < 0 || buyInMax < buyInMin
    )
      None
    else
      Some(Settings(
        playersCount, maxPlayers, minPlayers,
        smallBlind, bigBlind, ante,
        buyInMin, buyInMax
      ))


  implicit val encodeLobby: Encoder[PokerSettings] = Encoder.instance {
    case s: Settings => s.asJson
  }
}
