package mixzpoker.domain.auth

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

final case class AuthToken(value: String) extends AnyVal {
  override def toString: String = value
}

object AuthToken {
  def fromString(str: String): AuthToken =
    AuthToken(str)

  def fromUUID(uuid: UUID): AuthToken = {
    val b64RandomStr = Base64.getEncoder.encodeToString(uuid.toString.getBytes(StandardCharsets.UTF_8))
    AuthToken(b64RandomStr)
  }
}