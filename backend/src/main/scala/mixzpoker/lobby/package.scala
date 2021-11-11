package mixzpoker

import mixzpoker.lobby.LobbyError._


package object lobby {
  case class LobbyName(value: String) extends AnyVal

  object LobbyName {
    def fromString(string: String): ErrOr[LobbyName] = Right(LobbyName(string))
  }

}
