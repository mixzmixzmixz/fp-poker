package mixzpoker.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object UserDto {

  // signIn and signUp might be different in the future
  case class SignUpDto(userName: String, password: String)

  case class SignInDto(userName: String, password: String)

  case class UserDto(id: String, name: String, tokens: Int)

  implicit val signUpDtoDecoder: Decoder[SignUpDto] = deriveDecoder
  implicit val signUpDtoEncoder: Encoder[SignUpDto] = deriveEncoder

  implicit val signInDtoDecoder: Decoder[SignInDto] = deriveDecoder
  implicit val signInDtoEncoder: Encoder[SignInDto] = deriveEncoder

  implicit val userDtoDecoder: Decoder[UserDto] = deriveDecoder
  implicit val userDtoEncoder: Encoder[UserDto] = deriveEncoder
}
