package mixzpoker

trait AppError

object AppError {
  type ErrOr[A] = Either[AppError, A]

  case object SomeError extends AppError
}
