package mixzpoker.user

import io.circe.generic.JsonCodec


sealed trait UserDto

object UserDto {

  @JsonCodec
  case class User(id: String, name: String, tokens: Int)

  @JsonCodec
  case class CreateUserRequest(name: String)

}