package mixzpoker.domain.user

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token


// todo tags?
//package private codecs

//todo rename amout to balance
final case class User(id: UserId, name: UserName, password: UserPassword, amount: Token) {
  override def equals(obj: Any): Boolean = obj match {
    case u: User => name.value == u.name.value
    case _       => false
  }
}


object User {
  def create(id: UserId, name: UserName, password: UserPassword, balance: Token = 1000): Either[UserError, User] =
    Right(User(id, name, password, balance))

  def empty: User = User(id = UserId.zero, name = UserName.empty, UserPassword.empty, amount = 0)

  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder
}
