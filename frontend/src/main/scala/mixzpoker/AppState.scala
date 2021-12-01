package mixzpoker


sealed trait AppState

object AppState {
  case object NotLoaded extends AppState
  case object Unauthorized extends AppState
  case object Authorized extends AppState
}

