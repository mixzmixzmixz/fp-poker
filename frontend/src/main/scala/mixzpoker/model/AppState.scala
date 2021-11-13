package mixzpoker.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder


sealed trait AppState

object AppState {
  case object AppNotLoaded extends AppState
  case object Unauthorized extends AppState
  case class AppContext(
    name: String,
    balance: Int,
    token: String = ""
  ) extends AppState

  def notLoaded: AppState = AppNotLoaded


  object AppContext {
    implicit val appUserDecoder: Decoder[AppContext] = deriveDecoder
  }
}

