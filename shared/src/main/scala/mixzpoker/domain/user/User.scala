package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token
import mixzpoker.domain.user.UserError._



// todo tags?
//package private codecs
final case class User(id: UserId, name: UserName, password: UserPassword, amount: Token) {
  def checkPassword(pw: String): Either[UserError, Unit] =
    Either.cond(password == UserPassword.fromString(pw), (), WrongPassword)

  override def equals(obj: Any): Boolean = obj match {
    case u: User => name.value == u.name.value
    case _       => false
  }
}


object User {
  def create(name: String, password: String): Either[UserError, User] =
    Right(User(UserId.fromRandom, UserName(name), UserPassword.fromString(password), 1000))

  def empty: User = User(id = UserId.zero, name = UserName.empty, UserPassword.empty, amount = 0)

  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder
}
