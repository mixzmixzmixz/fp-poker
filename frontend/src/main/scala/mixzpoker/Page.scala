package mixzpoker

import upickle.default._

sealed trait Page

object Page {
  case object SignIn extends Page
  case object SignUp extends Page
  case object Redirect extends Page

  sealed trait AppPage extends Page
  case object Lobbies extends AppPage
  case class Lobby(name: String) extends AppPage
  case object PokerGames extends AppPage
  case class PokerGame(id: String) extends AppPage

  implicit val rwLobbyAppPage: ReadWriter[Lobby] = macroRW
  implicit val rwPokerGameAppPage: ReadWriter[PokerGame] = macroRW
  implicit val rwAppPage: ReadWriter[AppPage] = macroRW
  implicit val rwPage: ReadWriter[Page] = macroRW

}
