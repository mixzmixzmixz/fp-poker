package mixzpoker.auth

import io.circe.generic.JsonCodec

sealed trait AuthDto

object AuthDto {

  @JsonCodec
  case class SignInRequest(userName: String, password: String)

  @JsonCodec
  case class RegisterUserRequest(userName: String, password: String)

}
