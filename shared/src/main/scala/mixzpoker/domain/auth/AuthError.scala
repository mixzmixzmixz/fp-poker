package mixzpoker.domain.auth

import mixzpoker.domain.AppError
import mixzpoker.domain.user.UserError

sealed trait AuthError extends AppError

object AuthError {
  final case object NoAuthorizationHeader extends AuthError
  final case object InvalidToken extends AuthError
  final case object NoSuchToken extends AuthError

  final case object NoSuchUser extends AuthError
  final case object UserAlreadyExist extends AuthError
  final case object WrongPassword extends AuthError
  final case class SignUpError(error: UserError) extends AuthError
}
