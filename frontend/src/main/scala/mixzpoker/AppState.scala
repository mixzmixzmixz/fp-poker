package mixzpoker


sealed trait AppState

object AppState {
  final case object NotLoaded extends AppState
  final case object Unauthorized extends AppState
  final case object Authorized extends AppState
}

