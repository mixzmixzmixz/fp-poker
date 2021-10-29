package mixzpoker.user

import io.circe.{Encoder, Json}
import io.circe.syntax._
import mixzpoker.domain.Token
import mixzpoker.user.UserError._


sealed trait User {
  def id: UserId
  def name: UserName
  def password: UserPassword
  def amount: Token

  def checkPassword(password: String): ErrOr[Unit]
}

object User {

  case class AnonymousUser(id: UserId, name: UserName, password: UserPassword, amount: Token) extends User {
    //todo should allow user to play without registration, hence it does not need a password
    //  anonymous user should only exist why its token is Active

    override def checkPassword(password: String): ErrOr[Unit] = ???
  }

  case class RegularUser(id: UserId, name: UserName, password: UserPassword, amount: Token) extends User {
    override def checkPassword(password: String): ErrOr[Unit] = for {
      userPassword <- UserPassword.fromString(password)
      _ <- if (userPassword == this.password) Right(()) else Left(WrongPassword)
    } yield ()
  }


  def newAnonymousUser(name: String): User = AnonymousUser(UserId.fromRandom, UserName(name), UserPassword("tmp"), 1000)

  def newUser(name: String, password: String): ErrOr[User] = for {
    userPassword <- UserPassword.fromString(password)
    userName = UserName(name)
  } yield RegularUser(UserId.fromRandom, userName, userPassword, 1000)

  implicit val encodeUser: Encoder[User] = Encoder.instance {
    case AnonymousUser(_, name, _, _) => Json.obj("name" -> Json.fromString(name.value))
    case RegularUser(_, name, _, _) => Json.obj("name" -> Json.fromString(name.value))
  }
}
