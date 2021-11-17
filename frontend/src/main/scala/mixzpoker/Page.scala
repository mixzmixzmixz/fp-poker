package mixzpoker

import upickle.default._

sealed trait Page

object Page {
  case object SignInPage extends Page
  case object SignUpPage extends Page
  case object MainPage extends Page

  implicit val rw: ReadWriter[Page] = macroRW

}
