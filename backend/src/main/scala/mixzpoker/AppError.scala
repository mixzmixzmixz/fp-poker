package mixzpoker

trait AppError extends Exception

object AppError {
  type ErrOr[A] = Either[AppError, A]

  case object SomeError extends AppError
}
