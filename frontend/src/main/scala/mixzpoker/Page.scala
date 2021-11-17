package mixzpoker

import upickle.default._

sealed trait Page

object Page {
  case object SignInPage extends Page
  case object SignUpPage extends Page
  case object RedirectPage extends Page

  sealed trait AppPage extends Page
  case object LobbiesPage extends AppPage
  case object GamesPage extends AppPage

  implicit val rwAppPage: ReadWriter[AppPage] = macroRW
  implicit val rwPage: ReadWriter[Page] = macroRW

}
