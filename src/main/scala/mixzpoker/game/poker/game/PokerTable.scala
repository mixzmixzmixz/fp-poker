package mixzpoker.game.poker.game

import mixzpoker.game.poker.player.PokerPlayerState.{ActivePlayer, FoldedPlayer, JoinedPlayer}
import mixzpoker.game.poker.PokerError._
import mixzpoker.game.poker.player.{PokerPlayer, PokerPlayerState}
import mixzpoker.user.UserId


trait PokerTable {
  def size: Int

  def playersCount: Int

  def playerLeaves(player: PokerPlayer): ErrOr[PokerTable]

  def playerJoins(player: PokerPlayer): ErrOr[PokerTable]

  def dealerSeat: Int

  def smallBlindSeat: Int

  def bigBlindSeat: Int

  def playerToActSeat: Int

  def playerToAct: ErrOr[PokerPlayerState]

  def getPlayerToActState(player: PokerPlayer): ErrOr[ActivePlayer]

  def moveTransition: ErrOr[PokerTable]

  def updatePlayer(newPlayerState: PokerPlayerState): ErrOr[PokerTable]

}

object PokerTable {
  private case class PokerTableImpl(
    table: Map[UserId, (Int, PokerPlayerState)], size: Int, playerToActSeat: Int, dealerSeat: Int
  ) extends PokerTable {

    private def players: List[PokerPlayerState] = table.values.toList.map(_._2)

    private def activePlayers: List[ActivePlayer] = players.collect { case ap: ActivePlayer => ap }

    private def findPlayer(player: PokerPlayer): ErrOr[(Int, PokerPlayerState)] =
      table.get(player.user.id).toRight(SomeError(s"No such player $player"))

    private def firstEmptySeat: ErrOr[Int] = {
      val seatsTaken = table.values.toSet[(Int, PokerPlayerState)].map[Int] { case (i, state) => i}
      (0 until size).filterNot(seatsTaken.contains).headOption.toRight(SomeError("All seats taken!"))
    }

    override def playersCount: Int = players.length

    override def playerLeaves(player: PokerPlayer): ErrOr[PokerTable] =
      findPlayer(player).flatMap { case (seat, state) =>
        val pt = copy(table = table - player.user.id)
        if (seat == playerToActSeat) pt.moveTransition else Right(pt)
      }

    override def playerJoins(player: PokerPlayer): ErrOr[PokerTable] =
      if (table.contains(player.user.id))
        Left(SomeError(s"PokerPlayer $player already at the table"))
      else
        firstEmptySeat.map(seat => copy(table = table.updated(player.user.id, (seat, FoldedPlayer(player)))))

    override def smallBlindSeat: Int = dealerSeat + 1 % size

    override def bigBlindSeat: Int = dealerSeat + 2 % size

    override def playerToAct: ErrOr[PokerPlayerState] =
      table.find(_._2._1 == playerToActSeat)
        .map(_._2._2)
        .toRight(SomeError(s"No player on seat $playerToActSeat"))

    override def getPlayerToActState(player: PokerPlayer): ErrOr[ActivePlayer] =
      table.get(player.user.id)
        .toRight(SomeError(s"No such player $player"))
        .flatMap { case (seat, ps) =>
          if (seat == playerToActSeat) ps match {
              case ap: ActivePlayer => Right(ap)
              case _ => Left(SomeError("Should be ActivePlayer"))
            }
          else
            Left(SomeError(s"PokerPlayer's seat ($seat) does not correspond playerToAct seat"))
        }

    override def moveTransition: ErrOr[PokerTable] = {
      def isActivePlayer(p: PokerPlayerState): Boolean = p match {
        case ActivePlayer(_, _) => true
        case _ => false
      }

      val seatsTaken = table.values.toSet.filter(x => isActivePlayer(x._2)).map(_._1) //filter out active users
      ((playerToActSeat + 1) until (playerToActSeat + size))
        .map(_ % size).find(seatsTaken.contains)
        .toRight(SomeError("No other player to act!"))
        .map(seat => copy(playerToActSeat = seat))
    }

    override def updatePlayer(newPlayerState: PokerPlayerState): ErrOr[PokerTable] = for {
      seat0 <- findPlayer(newPlayerState.player)
      seat = seat0._1
    } yield copy(table = (table - newPlayerState.player.user.id).updated(newPlayerState.player.user.id, (seat, newPlayerState)))
  }

  def fromPlayers(players: Seq[PokerPlayer], size: Int): ErrOr[PokerTable] =
    if (players.length < 2)
      Left(SomeError("At least 2 users should be presented at the table!"))
    else if (players.length > size)
      Left(SomeError(s"There is more users ${players.length} then allowed $size"))
    else {
      val table = players.zipWithIndex.map { case (player, i) =>
        (player.user.id, (i, JoinedPlayer(player)))
      }.toMap
      val dealerSeat = 0
      val playerToActSeat = dealerSeat + 3 % size  // dealer, smallBlind, bigBlind, playerToAct

      Right(PokerTableImpl(table, size, playerToActSeat, dealerSeat ))
    }
}
