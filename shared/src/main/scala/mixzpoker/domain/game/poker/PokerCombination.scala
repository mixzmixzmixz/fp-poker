package mixzpoker.domain.game.poker

import io.circe.syntax._
import cats.syntax.functor._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import mixzpoker.domain.game.core.Card
//copied from evo bootcamp test assignment

sealed trait PokerCombination {

  protected def createBitmask(handValue: Int, highs: List[Int], lows: List[Int] = List()): Long = {
    //---Hand Values(9)--||-----Highs--||-----Lows---|
    //SF,4,32,F,S,3,22,2,1,AKQJT98765432,AKQJT98765432  -> 13*2 + 9 = 35 bit
    val bitMask: Long = (1: Long) << (handValue + 2*13)
    val bitMaskHighs = highs.foldLeft(bitMask) { case (acc, x) => acc | (1 << (13 + x-1)) }
    lows.foldLeft(bitMaskHighs) { case (acc, x) => acc | (1 << (x-1)) }
  }

  def score: Long

  def scoreBinaryString: String = ("0"*(35-score.toBinaryString.length)) + score.toBinaryString
}

object PokerCombination {

  case class StraightFlush(sfcards: List[Card]) extends PokerCombination {
    val maxRank = {
      val maxRank = sfcards.map(_.rank.asInt).max
      // if there is a K in the fcards then it's TJQKA straight with A as its highest
      // otherwise its A2345 straight with 5 as its highest (rank 4)
      if (maxRank == 13 && !sfcards.exists(_.rank.asInt == 12)) 4 else maxRank
    }
    def score: Long = createBitmask(8, List(maxRank))
  }

  case class FourOfAKind(fourCards: List[Card], kicker: Card) extends PokerCombination {
    def score: Long = createBitmask(7, List(fourCards.head.rank.asInt), List(kicker.rank.asInt))
  }

  case class FullHouse(threeCards: List[Card], twoCards: List[Card]) extends PokerCombination {
    def score: Long = createBitmask(6, List(threeCards.head.rank.asInt), List(twoCards.head.rank.asInt))
  }

  case class Flush(fcards: List[Card]) extends PokerCombination {
    def score: Long = createBitmask(5, fcards.map(_.rank.asInt))
  }

  case class Straight(scards: List[Card]) extends PokerCombination {
    val maxRank = {
      val maxRank = scards.map(_.rank.asInt).max
      // if there is a K in the fcards then it's TJQKA straight with A as its highest
      // otherwise its A2345 straight with 5 as its highest (rank 4)
      if (maxRank == 13 && !scards.exists(_.rank.asInt == 12)) 4 else maxRank
    }
    def score: Long = createBitmask(4, List(maxRank))
  }

  case class ThreeOfAKind(threeCards: List[Card], kickers: List[Card]) extends PokerCombination {
    def score: Long = createBitmask(3, List(threeCards.head.rank.asInt), kickers.map(_.rank.asInt))
  }

  case class TwoPairs(highPair: List[Card], lowPair: List[Card], kicker: Card) extends PokerCombination {
    def score: Long =
      createBitmask(2, List(highPair.head.rank.asInt, lowPair.head.rank.asInt), List(kicker.rank.asInt))
  }

  case class Pair(pair: List[Card], kickers: List[Card]) extends PokerCombination {
    def score: Long = createBitmask(1, List(pair.head.rank.asInt), kickers.map(_.rank.asInt))
  }

  case class HighCard(kickers: List[Card]) extends PokerCombination {
    def score: Long = createBitmask(0, List(), kickers.map(_.rank.asInt))
  }


  def isFlush(cards: List[Card]): Boolean = cards.map(_.suit).toSet.size == 1

  def isStraight(cards: List[Card]): Boolean = {
    val rankSet = cards.map(_.rank.asInt).toSet
    // straight consists of 5 fcards with different ranks
    if (rankSet.size != 5) false
    else if (rankSet.contains(13)) {
      //special case with Ace treated as 'One' (A 2 3 4 5)
      val aceAsOneSet = (rankSet - 13) + 0  // 'One' rank would be 0
      aceAsOneSet.max - aceAsOneSet.min == 4 || rankSet.max - rankSet.min == 4
    } else {
      // regular case, 2 3 4 5 6 => 6-2 == 4
      rankSet.max - rankSet.min == 4
    }
  }

  def apply(cards: List[Card]): PokerCombination = {
    val groupedByRank = cards.groupBy(_.rank)

    if (isStraight(cards) && isFlush(cards))
      StraightFlush(cards)
    else if (groupedByRank.size == 2 && groupedByRank.values.exists(_.length == 4))
      FourOfAKind(
        groupedByRank.values.filter(_.length == 4).head,
        groupedByRank.values.filter(_.length == 1).head.head
      )
    else if (groupedByRank.size == 2 && groupedByRank.values.exists(_.length == 3))
      FullHouse(
        groupedByRank.values.filter(_.length == 3).head,
        groupedByRank.values.filter(_.length == 2).head
      )
    else if (isFlush(cards))
      Flush(cards)
    else if (isStraight(cards))
      Straight(cards)
    else if (groupedByRank.size == 3 && groupedByRank.values.exists(_.length == 3))
      ThreeOfAKind(
        threeCards = groupedByRank.values.filter(_.length == 3).head,
        kickers = groupedByRank.values.filter(_.length == 1).flatten.toList.sortBy(_.rank).reverse
      )
    else if (groupedByRank.size == 3 && groupedByRank.values.count(_.length == 2) == 2) {
      val pairs = groupedByRank.values.filter(_.length == 2).toList.sortBy(_.head.rank)
      val kicker = groupedByRank.values.filter(_.length == 1).head.head
      TwoPairs(pairs(1), pairs(0), kicker)
    } else if (groupedByRank.size == 4 && groupedByRank.values.exists(_.length == 2))
      Pair(
        pair = groupedByRank.values.filter(_.length == 2).head,
        kickers = groupedByRank.values.filter(_.length == 1).flatten.toList.sortBy(_.rank).reverse
      )
    else
      HighCard(cards.sortBy(_.rank).reverse)
  }

  implicit val sfEncoder: Encoder[StraightFlush] = deriveEncoder
  implicit val sfDecoder: Decoder[StraightFlush] = deriveDecoder

  implicit val foakEncoder: Encoder[FourOfAKind] = deriveEncoder
  implicit val foakDecoder: Decoder[FourOfAKind] = deriveDecoder

  implicit val fhEncoder: Encoder[FullHouse] = deriveEncoder
  implicit val fhDecoder: Decoder[FullHouse] = deriveDecoder

  implicit val fEncoder: Encoder[Flush] = deriveEncoder
  implicit val fDecoder: Decoder[Flush] = deriveDecoder

  implicit val sEncoder: Encoder[Straight] = deriveEncoder
  implicit val sDecoder: Decoder[Straight] = deriveDecoder

  implicit val toakEncoder: Encoder[ThreeOfAKind] = deriveEncoder
  implicit val toakDecoder: Decoder[ThreeOfAKind] = deriveDecoder

  implicit val tpEncoder: Encoder[TwoPairs] = deriveEncoder
  implicit val tpDecoder: Decoder[TwoPairs] = deriveDecoder

  implicit val pEncoder: Encoder[Pair] = deriveEncoder
  implicit val pDecoder: Decoder[Pair] = deriveDecoder

  implicit val hcEncoder: Encoder[HighCard] = deriveEncoder
  implicit val hcDecoder: Decoder[HighCard] = deriveDecoder

  implicit val combEncoder: Encoder[PokerCombination] = Encoder.instance {
    case c: StraightFlush => c.asJson
    case c: FourOfAKind   => c.asJson
    case c: FullHouse     => c.asJson
    case c: Flush         => c.asJson
    case c: Straight      => c.asJson
    case c: ThreeOfAKind  => c.asJson
    case c: TwoPairs      => c.asJson
    case c: Pair          => c.asJson
    case c: HighCard      => c.asJson
  }

  implicit val combDecoder: Decoder[PokerCombination] =
    List[Decoder[PokerCombination]](
      Decoder[StraightFlush].widen,
      Decoder[FourOfAKind].widen,
      Decoder[FullHouse].widen,
      Decoder[Flush].widen,
      Decoder[Straight].widen,
      Decoder[ThreeOfAKind].widen,
      Decoder[TwoPairs].widen,
      Decoder[Pair].widen,
      Decoder[HighCard].widen
    ).reduceLeft(_ or _)
}

// Attempt 1
// bit map
// SF, 4, 32, F, S, 3, 22, 2, 1, A, K, Q, J, T, 9, 8, 7, 6, 5, 4, 3, 2
//
//eg 4cKs4h8s7s
// 0   0  0   0  0  0  0   1  0  0  1  0  0  0  0  1  1  0  0  0  0  0  -> pair with kickers K, 8, 7
//eg 2h3h4h5d6d
// 0   0  0   0  1  0  0   0  0  0  0  0  0  0  0  0  0  1  0  0  0  0  -> straight starting from 6

//eg 2h2h4h4d6d  -> two pairs (2s, 3s) and a kicker 6
// 0   0  0   0  0  0  1   0  0  0  0  0  0  0  0  0  0  1  0  0  0  0

//eg 7h7h4h4d6d  -> two pairs (7s, 3s) and a kicker 6
// 0   0  0   0  0  0  1   0  0  0  0  0  0  0  0  0  0  1  0  0  0  0

//Attempt2
//---Hand Values(9)--||--threes----||-----pairs--||-----solo---|
//SF,4,32,F,S,3,22,2,1,AKQJT98765432,AKQJT98765432,AKQJT98765432  -> 13*3 + 9 = 48 bit
//eg 4cKs4h8s7s -> pair with kickers K, 8, 7
//0  0 0  0 0 0 0  1 0 0000000000000 0000000000000 0100001100000
//eg 4c4s4h4dKs -> Four of 4 with kicker K
//0  1 0  0 0 0 0  0 0 0000000000100 0000000000000 0100000000000
//eg 4c4s5h5s7s -> Two Pairs (4, 5) with kicker 7
//0  0 0  0 0 0 1  0 0 0000000000100 0000000001100 0000000100000
//eg 4c4s5h5s5d -> FUll House (4, 5)
//0  0 0  1 0 0 0  0 0 0000000001000 0000000000100 0000000000000

// Only 2 sets are needed actually
//---Hand Values(9)--||-----Highs--||-----Lows---|
//SF,4,32,F,S,3,22,2,1,AKQJT98765432,AKQJT98765432  -> 13*2 + 9 = 35 bit
//eg 4cKs4h8s7s -> pair with kickers K, 8, 7
//0  0 0  0 0 0 0  1 0 0000000000000 0100001100000
//eg 4c4s4h4dKs -> Four of 4 with kicker K
//0  1 0  0 0 0 0  0 0 0000000000100 0100000000000
//eg 4c4s5h5s7s -> Two Pairs (4, 5) with kicker 7
//0  0 0  0 0 0 1  0 0 0000000001100 0000000100000
//eg 4c4s5h5s5d -> FUll House (4, 5)
//0  0 0  1 0 0 0  0 0 0000000001000 0000000000100
