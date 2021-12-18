package mixzpoker.domain.auth

import io.circe.generic.JsonCodec

sealed trait AuthRequest

object AuthRequest {

  @JsonCodec
  case class SignInRequest(userName: String, password: String) extends AuthRequest

  @JsonCodec
  case class RegisterUserRequest(userName: String, password: String) extends AuthRequest

}
