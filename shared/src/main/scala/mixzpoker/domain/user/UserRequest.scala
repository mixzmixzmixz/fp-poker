package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token

sealed trait UserRequest


object UserRequest {


  // signIn and signUp might be different in the future
  final case class SignUpRequest(userName: String, password: String) extends UserRequest

  final case class SignInRequest(userName: String, password: String) extends UserRequest

  final case class GimmeMoneyRequest(money: Token) extends UserRequest


  implicit val signUpRequestDecoder: Decoder[SignUpRequest] = deriveDecoder
  implicit val signUpRequestEncoder: Encoder[SignUpRequest] = deriveEncoder

  implicit val signInRequestDecoder: Decoder[SignInRequest] = deriveDecoder
  implicit val signInRequestEncoder: Encoder[SignInRequest] = deriveEncoder

  implicit val gimmeMoneyRequestDecoder: Decoder[GimmeMoneyRequest] = deriveDecoder
  implicit val gimmeMoneyRequestEncoder: Encoder[GimmeMoneyRequest] = deriveEncoder
}