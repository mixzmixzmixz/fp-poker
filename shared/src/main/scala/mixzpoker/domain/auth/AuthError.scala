package mixzpoker.domain.auth

import mixzpoker.domain.AppError
import mixzpoker.domain.user.UserError

sealed trait AuthError extends AppError

object AuthError {
  case object NoAuthorizationHeader extends AuthError
  case object InvalidToken extends AuthError
  case object NoSuchToken extends AuthError
  case class UserErrorWrapper(userError: UserError) extends AuthError
}
