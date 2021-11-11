package mixzpoker

import cats.effect.Sync
import cats.implicits._
import io.circe.{Decoder, Encoder}

import java.util.UUID
import scala.util.Try

import mixzpoker.game.GameError._

package object game {
  case class GameId(value: UUID) extends AnyVal {
    override def toString: String = value.toString.replace("-", "")
  }

  object GameId {
    def fromRandom[F[_]: Sync]: F[GameId] = GameId(UUID.randomUUID()).pure[F]

    def fromString(str: String): ErrOr[GameId] =
      Try(UUID.fromString(str.replaceFirst(
        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
        "$1-$2-$3-$4-$5"
      ))).toOption.map(GameId(_))
        .toRight(InvalidGameIdFormat)

    implicit val encoderGameId: Encoder[GameId] =
      Encoder[String].contramap(a => a.toString)

    implicit val decoderGameId: Decoder[GameId] =
      Decoder[String].emap(s => GameId.fromString(s).leftMap(_.toString))
  }

  case class EventId(value: UUID) extends AnyVal {
    override def toString: String = value.toString.replace("-", "")
  }

  object EventId {
    def fromRandom[F[_]: Sync]: F[EventId] = EventId(UUID.randomUUID()).pure[F]

    def fromString(str: String): ErrOr[EventId] =
      Try(UUID.fromString(str.replaceFirst(
        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
        "$1-$2-$3-$4-$5"
      ))).toOption.map(EventId(_))
        .toRight(InvalidGameIdFormat)

    implicit val encoderEventId: Encoder[EventId] =
      Encoder[String].contramap(a => a.toString)

    implicit val decoderEventId: Decoder[EventId] =
      Decoder[String].emap(s => EventId.fromString(s).leftMap(_.toString))
  }
}
