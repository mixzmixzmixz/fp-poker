package mixzpoker

import java.util.{Base64, UUID}
import java.nio.charset.StandardCharsets

import mixzpoker.auth.AuthError._


package object auth {

  case class AuthToken(value: String) extends AnyVal {
    override def toString: String = value
  }

  object AuthToken {
    def fromString(str: String): ErrOr[AuthToken] =
      Right(AuthToken(str))

    def fromRandom: AuthToken = {
      val b64RandomStr = Base64.getEncoder.encodeToString(UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8))
      AuthToken(b64RandomStr)
    }
  }

}
