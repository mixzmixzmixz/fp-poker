package mixzpoker.domain

trait AppError extends Exception

object AppError {
  type ErrOr[A] = Either[AppError, A]

  final case object SomeError extends AppError
}
