package mixzpoker

sealed trait AppError

object AppError {
  final case object NoError extends AppError
  final case class GeneralError(message: String) extends AppError
}
