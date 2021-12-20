package mixzpoker.domain.auth

import io.circe.generic.JsonCodec
import mixzpoker.domain.user.{UserName, UserPassword}

sealed trait AuthRequest

object AuthRequest {

  @JsonCodec
  final case class SignInRequest(userName: UserName, password: UserPassword) extends AuthRequest

  @JsonCodec
  final case class RegisterUserRequest(userName: UserName, password: UserPassword) extends AuthRequest

}
