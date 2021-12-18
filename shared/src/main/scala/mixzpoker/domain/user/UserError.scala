package mixzpoker.domain.user

import mixzpoker.domain.AppError


sealed trait UserError extends AppError

object UserError {

  final case class NoSuchUser(name: UserName) extends UserError
  final case class UserAlreadyExist(name: UserName) extends UserError
  final case object WrongPassword extends UserError
}
