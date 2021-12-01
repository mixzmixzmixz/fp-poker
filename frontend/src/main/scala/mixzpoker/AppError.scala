package mixzpoker

sealed trait AppError

object AppError {
  case object NoError extends AppError
  case class GeneralError(message: String) extends AppError
}
