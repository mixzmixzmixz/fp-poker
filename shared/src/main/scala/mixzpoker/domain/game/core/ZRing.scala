package mixzpoker.domain.game.core



object ZRing {

  def range(n: Int, from: Int, to: Int): List[Int] = {
    val _from = from % n
    val _to = to % n

    if (_from > _to)
      (_from until n).toList ::: (0 to _to).toList
    else
      (_to to _from ).toList
  }


}
