package mixzpoker.domain

import scala.util.control.NoStackTrace

trait AppError extends NoStackTrace

object AppError {
  type ErrOr[A] = Either[AppError, A]

  final case object SomeError extends AppError
}
