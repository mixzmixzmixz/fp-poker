package mixzpoker.user

import mixzpoker.domain.Token
import mixzpoker.user.UserError._
import mixzpoker.domain.User.UserDto._

sealed trait User {
  def id: UserId
  def name: UserName
  def password: UserPassword
  def amount: Token

  def dto: UserDto
  def checkPassword(password: String): ErrOr[Unit]
}

object User {

  case class AnonymousUser(id: UserId, name: UserName, password: UserPassword, amount: Token) extends User {
    //todo should allow user to play without registration, hence it does not need a password
    //  anonymous user should only exist why its token is Active

    override def checkPassword(password: String): ErrOr[Unit] = ???

    override def dto: UserDto = ???
  }

  case class RegularUser(id: UserId, name: UserName, password: UserPassword, amount: Token) extends User {
    override def dto: UserDto = UserDto(id = id.toString, name = name.toString, tokens = amount)

    override def checkPassword(password: String): ErrOr[Unit] = for {
      userPassword <- UserPassword.fromString(password)
      _ <- if (userPassword == this.password) Right(()) else Left(WrongPassword)
    } yield ()
  }


  def newAnonymousUser(name: String): User = AnonymousUser(UserId.fromRandom, UserName(name), UserPassword("tmp"), 1000)

  def create(name: String, password: String): ErrOr[User] = for {
    userPassword <- UserPassword.fromString(password)
    userName = UserName(name)
  } yield RegularUser(UserId.fromRandom, userName, userPassword, 1000)
}
