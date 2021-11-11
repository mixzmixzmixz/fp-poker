package mixzpoker.game.poker.game

import io.circe.generic.JsonCodec
import mixzpoker.game.poker.PokerError
import mixzpoker.game.poker.PokerError._
import mixzpoker.game.poker.player.PokerPlayer
import mixzpoker.user.UserId


@JsonCodec
case class PokerTable(size: Int, dealerSeat: Int, playerToActSeat: Int, players: List[PokerPlayer]) {
  def playersCount: Int = players.length

  def playerLeaves(userId: UserId): ErrOr[PokerTable] = for {
    //todo think of all corner cases
    p <- players.find(_.userId == userId).toRight[PokerError](NoPlayerWithUserId(userId))
  } yield copy(players = players.toSet.-(p).toList)

  def playerJoins(userId: UserId): ErrOr[PokerTable] = ???

  def smallBlindSeat: Int = dealerSeat + 1
  def bigBlindSeat: Int = dealerSeat + 2

  def playerToAct(userId: UserId): ErrOr[PokerPlayer] = for {
    p <- players.find(_.seat == playerToActSeat)
      .toRight[PokerError](NoPlayerOnSeat(playerToActSeat))
    _ <- Either.cond(p.userId == userId, (), WrongUserId)
  } yield p


  def moveTransition: ErrOr[PokerPlayer] = ???
  def updatePlayer(player: PokerPlayer): ErrOr[PokerTable] = ???

}

object PokerTable {

  def fromPlayers(players: List[PokerPlayer], size: Int): ErrOr[PokerTable] =
    players.length match {
      case l if l < 2 => Left(NotEnoughPlayers)
      case l if l > size => Left(TooManyPlayers)
      case _ => Right(PokerTable(
        size = size,
        dealerSeat = 0,
        playerToActSeat = 3 % size,
        players = players
      ))
    }
}
