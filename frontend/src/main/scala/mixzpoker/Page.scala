package mixzpoker

import upickle.default._

sealed trait Page

object Page {
  final case object SignIn extends Page
  final case object SignUp extends Page
  final case object Redirect extends Page

  sealed trait AppPage extends Page
  final case object Lobbies extends AppPage
  final case class Lobby(name: String) extends AppPage
  final case object PokerGames extends AppPage
  final case class PokerGame(id: String) extends AppPage

  implicit val rwLobbyAppPage: ReadWriter[Lobby] = macroRW
  implicit val rwPokerGameAppPage: ReadWriter[PokerGame] = macroRW
  implicit val rwAppPage: ReadWriter[AppPage] = macroRW
  implicit val rwPage: ReadWriter[Page] = macroRW

}
