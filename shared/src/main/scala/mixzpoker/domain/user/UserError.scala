package mixzpoker.domain.user

import mixzpoker.domain.AppError


sealed trait UserError extends AppError

object UserError {
  final case object NoSuchUser extends UserError
  final case object UserAlreadyExist extends UserError
  final case object WrongPassword extends UserError
}
