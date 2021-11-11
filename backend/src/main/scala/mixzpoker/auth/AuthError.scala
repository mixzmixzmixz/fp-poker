package mixzpoker.auth

import mixzpoker.user.UserError

sealed trait AuthError

object AuthError {
  type ErrOr[A] = Either[AuthError, A]

  case object NoAuthorizationHeader extends AuthError
  case object InvalidToken extends AuthError
  case object NoSuchToken extends AuthError
  case class UserErrorWrapper(userError: UserError) extends AuthError
}
