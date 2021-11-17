package mixzpoker.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder


sealed trait AppState

object AppState {
  case object AppNotLoaded extends AppState
  case object Unauthorized extends AppState
  case class AppUserInfo(id: String, name: String, tokens: Int) extends AppState

  object AppUserInfo {
    implicit val appUserDecoder: Decoder[AppUserInfo] = deriveDecoder
  }
}

