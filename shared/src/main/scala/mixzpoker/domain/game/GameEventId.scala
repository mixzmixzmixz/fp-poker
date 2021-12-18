package mixzpoker.domain.game

import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

final case class GameEventId(value: UUID) extends AnyVal {
  override def toString: String = value.toString.replace("-", "")
}

object GameEventId {
  def fromUUID(uuid: UUID): GameEventId = GameEventId(uuid)

  def fromString(str: String): Either[String, GameEventId] =
    Try(UUID.fromString(str.replaceFirst(
      "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
      "$1-$2-$3-$4-$5"
    ))).toOption.map(GameEventId(_)).toRight("InvalidGameIdFormat")

  implicit val encoderEventId: Encoder[GameEventId] = Encoder[String].contramap(_.toString)
  implicit val decoderEventId: Decoder[GameEventId] = Decoder[String].emap(GameEventId.fromString)
}
