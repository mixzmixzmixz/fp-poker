package mixzpoker.domain

import java.time.Instant
import java.util.UUID

package object lobby {

  case class LobbyInput(id: UUID, instant: Instant, message: LobbyInputMessage)

  case class LobbyOutput(id: UUID, instant: Instant, message: LobbyOutputMessage)
}
