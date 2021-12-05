package mixzpoker.user

import mixzpoker.AppError
import mixzpoker.domain.user.UserName


sealed trait UserError extends AppError

object UserError {
  type ErrOr[A] = Either[UserError, A]

  case class NoSuchUser(name: UserName) extends UserError
  case class UserAlreadyExist(name: UserName) extends UserError

  case object WrongPassword extends UserError
}
