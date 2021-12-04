package mixzpoker.domain.game

import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

case class GameId(value: UUID) extends AnyVal {
  override def toString: String = value.toString.replace("-", "")
}

object GameId {
  def fromUUID(uuid: UUID): GameId = GameId(uuid)

  def fromString(str: String): Either[String, GameId] =
    Try(UUID.fromString(str.replaceFirst(
      "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
      "$1-$2-$3-$4-$5"
    ))).toOption.map(GameId(_)).toRight("InvalidGameIdFormat")

  implicit val encoderGameId: Encoder[GameId] = Encoder[String].contramap(_.toString)

  implicit val decoderGameId: Decoder[GameId] = Decoder[String].emap(GameId.fromString)
}