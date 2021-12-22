package mixzpoker.domain.game.poker

import cats.effect.IO
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.Deck
import mixzpoker.domain.user.{UserId, UserName}
import org.scalatest.flatspec.AnyFlatSpec

import java.util.UUID
import scala.util.Random

class PokerGameSpec extends AnyFlatSpec {
  val game = PokerGame.create(
    gameId = GameId.fromUUID(UUID.randomUUID()),
    settings = PokerSettings.defaults,
    players = List(
      (UserId.fromInt(1), UserName("User1"), 1000),
      (UserId.fromInt(2), UserName("User2"), 1000)
    )
  )

  "New PokerGame" must "be ok" in {

    assert(game.players.values.map(_.seat).toList.sorted == List(0,1))
  }



  "PokerGame next round" must "be ok :)" in {
    val shuffledDeck = IO.delay { Random.shuffle(Deck.cards52) }.map(Deck.ofCards52).unsafeRunSync()

    val gameNR = game.nextRound(shuffledDeck)

    assert(shuffledDeck.size == 52 && gameNR.deck.size == 48)

  }
}
