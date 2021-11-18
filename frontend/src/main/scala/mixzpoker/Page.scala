package mixzpoker

import upickle.default._

sealed trait Page

object Page {
  case object SignIn extends Page
  case object SignUp extends Page
  case object Redirect extends Page

  sealed trait AppPage extends Page
  case object Lobbies extends AppPage
  case object Games extends AppPage

  implicit val rwAppPage: ReadWriter[AppPage] = macroRW
  implicit val rwPage: ReadWriter[Page] = macroRW

}
