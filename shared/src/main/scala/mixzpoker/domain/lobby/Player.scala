package mixzpoker.domain.lobby

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.Token
import mixzpoker.domain.user.User

final case class Player(
  user: User,
  buyIn: Token,
  ready: Boolean = false
)

object Player {

  implicit val lobbyEncoder: Encoder[Player] = deriveEncoder
  implicit val lobbyDecoder: Decoder[Player] = deriveDecoder
}
