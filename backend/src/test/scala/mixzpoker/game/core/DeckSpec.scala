package mixzpoker.game.core

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import mixzpoker.domain.game.core.Deck

import scala.util.Random


final class DeckSpec extends AnyFlatSpec {
  "Deck creation" must "create sorted deck" in {
    val deck = Deck.of52
    val cards = Deck.cards52
    val deckOfCards = Deck.ofCards52(cards)
    println(deck)
    println(cards)

    assert(deckOfCards == deck)
  }

  "Deck shuffle" must "produce shuffled deck" in {
    val deck1 = IO.delay { Random.shuffle(Deck.cards52) }.map(Deck.ofCards52).unsafeRunSync()
    val deck2 = IO.delay { Random.shuffle(Deck.cards52) }.map(Deck.ofCards52).unsafeRunSync()

    println(deck1)
    println(deck2)

    assert(deck1 != deck2 && deck1.size == 52)
  }

  "Shuffled deck" must "be able to deal cards" in {
    val deck1 = IO.delay { Random.shuffle(Deck.cards52) }.map(Deck.ofCards52).unsafeRunSync()
    val (cards, deck2) = deck1.getFirstNCards(2).get

    assert(cards.size == 2 && deck2.size == 50)
  }
}
