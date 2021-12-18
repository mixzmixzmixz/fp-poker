package mixzpoker.domain.auth

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

final case class AuthToken(value: String) extends AnyVal {
  override def toString: String = value
}

object AuthToken {
  def fromString(str: String): Either[AuthError, AuthToken] =
    Right(AuthToken(str))

  def fromRandom: AuthToken = {
    val b64RandomStr = Base64.getEncoder.encodeToString(UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8))
    AuthToken(b64RandomStr)
  }
}