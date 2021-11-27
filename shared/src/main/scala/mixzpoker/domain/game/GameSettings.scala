package mixzpoker.domain.game


import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import cats.syntax.functor._
import mixzpoker.domain.game.poker.PokerSettings


trait GameSettings {
  def maxPlayers: Int
  def minPlayers: Int

  def buyInMin: Int
  def buyInMax: Int
}

object GameSettings {
  implicit val gtEncoder: Encoder[GameSettings] = Encoder.instance {
    case settings: PokerSettings => settings.asJson
    case _ => Json.fromString("wrong settings")
  }

  implicit val gtDecoder: Decoder[GameSettings] = List[Decoder[GameSettings]](
    Decoder[PokerSettings].widen
  ).reduceLeft(_ or _)
}
