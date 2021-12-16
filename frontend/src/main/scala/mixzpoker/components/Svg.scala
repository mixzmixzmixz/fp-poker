package mixzpoker.components

import com.raquo.laminar.api.L._
import mixzpoker.domain.Token
import mixzpoker.domain.game.core.Card
import mixzpoker.domain.game.poker.{PokerPlayer, Pot}

object Svg {
  // poker table stolen from
  // https://upload.wikimedia.org/wikipedia/commons/c/c0/Texas_Hold%27em_Poker_Table_with_Blinds.svg

  def ChipSymbol(): SvgElement =
    svg.symbol(
      svg.idAttr := "Chip",
      svg.viewBox := "0 0 42 42",
      svg.circle(
        svg.cx := "21",
        svg.cy := "21",
        svg.r := "20.5",
        svg.stroke := "#000",
        svg.strokeWidth := "1",
        svg.fill := "#FFF"
      ),
      svg.path(
        svg.fill := "#FF0000",
        svg.stroke := "#231F20",
        svg.strokeMiterLimit := "10",
        svg.d := "M20.813,41.506c-3,0.004-6.767-0.883-10-2.75L31.188,3.25c-3.234-1.867-6.377-2.753-10.377-2.75L20.813,41.506L20.813,41.506z M38.851,10.915c1.503,2.597,2.618,6.302,2.618,10.035L0.532,21.057c0,3.735,0.804,6.899,2.807,10.362L38.851,10.915L38.851,10.915z M3.339,10.589c1.497-2.6,4.148-5.418,7.381-7.284l20.561,35.398c3.234-1.867,5.573-4.146,7.57-7.611L3.339,10.589L3.339,10.589z"
      ),
      svg.circle(
        svg.cx := "21",
        svg.cy := "21",
        svg.r := "15",
        svg.stroke := "#000",
        svg.strokeWidth := "1",
        svg.fill := "#FFF"
      )
    )

  def DealerButton(position: Int): SvgElement = {
    val (x,y) = position match {
      case 0 => (390, 460)
      case 1 => (240, 460)
      case 2 => (110, 300)
      case 3 => (110, 180)
      case 4 => (250, 95)
      case 5 => (400, 95)
      case 6 => (550, 95)
      case 7 => (650, 180)
      case 8 => (650, 300)
    }
    svg.g(
      svg.idAttr := "DealerButton",
      svg.circle(
        svg.fill := "#FFFFFF",
        svg.stroke := "#231F20",
        svg.strokeMiterLimit := "10",
        svg.cx := s"$x",
        svg.cy := s"$y",
        svg.r := "20.5",
      ),
      svg.text(
        svg.x := s"${x-18}",
        svg.y := s"${y+2}",
        svg.fontFamily := "DejaVu Sans, sans-serif",
        svg.fontWeight := "bold",
        svg.fontSize := "8",
        "DEALER"
      )
    )
  }

  def CardSymbol(): SvgElement =
    svg.symbol(
      svg.idAttr := "Card",
      svg.viewBox := "0 0 45.56 63.07",
      svg.defs(
        svg.pattern(
          svg.idAttr := "square",
          svg.width := "5",
          svg.height := "5",
          svg.patternUnits := "userSpaceOnUse",
          svg.rect(
            svg.x := "0",
            svg.y := "0",
            svg.width := "5",
            svg.height := "5",
            svg.fill := "#FFF"
          ),
          svg.rect(
            svg.x := "0",
            svg.y := "0",
            svg.width := "2.5",
            svg.height := "2.5",
            svg.fill := "#F00"
          ),
          svg.rect(
            svg.x := "2.5",
            svg.y := "2.5",
            svg.width := "2.5",
            svg.height := "2.5",
            svg.fill := "#F00"
          )
        )
      ),
      svg.rect(
        svg.x := "0.5",
        svg.y := "0.5",
        svg.rx := "2.5",
        svg.ry := "2.5",
        svg.width := "44.563",
        svg.height := "62.071",
        svg.style := "fill:#FFF;stroke-width:1;stroke:#000"
      ),
      svg.rect(
        svg.x := "3",
        svg.y := "3",
        svg.rx := "2",
        svg.ry := "2",
        svg.width := "39.563",
        svg.height := "57.071",
        svg.fill := "url(#square)"
      )
    )

  def CardsPair(position: Int): SvgElement = {
    val transformBotCard = position match {
      case 0 => "matrix(-0.9397 0.342 0.342 0.9397 470.4414 451.4072)"
      case 1 => "matrix(-0.9397 0.342 0.342 0.9397 286.5146 451.4072)"
      case 2 => "matrix(-0.866 -0.5 -0.5 0.866 151.7651 390.7607)"
      case 3 => "matrix(0.342 -0.9397 -0.9397 -0.342 156.2339 156.9268)"
      case 4 => "matrix(0.9397 -0.342 -0.342 -0.9397 287.2256 100.9028)"
      case 5 => "matrix(0.9397 -0.342 -0.342 -0.9397 471.1528 100.9028)"
      case 6 => "matrix(0.9397 -0.342 -0.342 -0.9397 648.25 100.9028)"
      case 7 => "matrix(0.866 0.5 0.5 -0.866 811.46 165.4668)"
      case 8 => "matrix(-0.342 0.9397 0.9397 0.342 810.2969 399.3008)"
    }

    val transformTopCard = position match {
      case 0 => "matrix(-1 0 0 1 479.4365 455.2002)"
      case 1 => "matrix(-1 0 0 1 295.5098 455.2002)"
      case 2 => "matrix(-0.6428 -0.766 -0.766 0.6428 154.6411 400.0889)"
      case 3 => "matrix(0.6428 -0.766 -0.766 -0.6428 147.5444 161.3789)"
      case 4 => "matrix(1 0 0 -1 278.2305 97.1089)"
      case 5 => "matrix(1 0 0 -1 462.1577 97.1089)"
      case 6 => "matrix(1 0 0 -1 639.2549 97.1089)"
      case 7 => "matrix(0.6428 0.766 0.766 -0.6428 808.584 156.1387)"
      case 8 => "matrix(-0.6428 0.766 0.766 0.6428 818.9863 394.8486)"
    }

    svg.g(
      svg.idAttr := s"CardsPairPos$position",
      svg.use(
        svg.xlinkHref := "#Card",
        svg.width := "45.562",
        svg.height := "63.07",
        svg.x := "-22.781",
        svg.y := "-31.535",
        svg.overflow := "visible",
        svg.transform := transformBotCard
      ),
      svg.use(
        svg.xlinkHref := "#Card",
        svg.width := "45.562",
        svg.height := "63.07",
        svg.x := "-22.781",
        svg.y := "-31.535",
        svg.overflow := "visible",
        svg.transform := transformTopCard
      )
    )
  }

  def Table(): SvgElement =
    svg.g(
      svg.idAttr := "Table",
      svg.path(
        svg.fill := "#AE431E",
        svg.stroke := "#000000",
        svg.strokeMiterLimit := "10",
        svg.d := "M752.582,552.5c113.505,0,205.518-121.348,205.518-272.12C958.1,129.608,866.087,8.5,752.582,8.5H207.418C93.914,8.5,1.9,129.608,1.9,280.38c0,150.772,92.014,272.12,205.518,272.12H752.582z"
      ),
      svg.path(
        svg.fill := "#8A8635",
        svg.stroke := "#000000",
        svg.strokeMiterLimit := "10",
        svg.d := "M720.313,505.5C820.38,505.5,901.5,405.343,901.5,280.9c0-124.441-81.12-224.4-181.188-224.4H239.688C139.62,56.5,58.5,156.459,58.5,280.9s81.12,224.6,181.188,224.6H720.313z"
      )

    )

  def ChipSingle(position: Int): SvgElement = {
    val transform = "matrix(1 0 0 -1 466.001 374.999)" //todo from position
    svg.g(
      svg.idAttr := s"SingleChipPos$position",
      svg.use(
        svg.xlinkHref := "#Chip",
        svg.width := "42.001",
        svg.height := "42.006",
        svg.x := "-21.001",
        svg.y := "-21.003",
        svg.overflow := "visible",
        svg.transform := transform
      )
    )
  }

  def ChipPair(position: Int): SvgElement = {
    val (x,y) = position match {
      case 0 => (420, 400)
      case 1 => (220, 400)
      case 2 => (120, 350)
      case 3 => (130, 180)
      case 4 => (200, 150)
      case 5 => (410, 150)
      case 6 => (560, 150)
      case 7 => (700, 180)
      case 8 => (700, 350)
    }
    val transformTop = s"matrix(1 0 0 -1 ${x+18} ${y+10})" //todo from position
    val transformBot = s"matrix(1 0 0 -1 $x $y)" //todo from position
    svg.g(
      svg.idAttr := s"PairChipPos$position",
      svg.use(
        svg.xlinkHref := "#Chip",
        svg.width := "42.001",
        svg.height := "42.006",
        svg.x := "-21.001",
        svg.y := "-21.003",
        svg.overflow := "visible",
        svg.transform := transformTop
      ),
      svg.use(
        svg.xlinkHref := "#Chip",
        svg.width := "42.001",
        svg.height := "42.006",
        svg.x := "-21.001",
        svg.y := "-21.003",
        svg.overflow := "visible",
        svg.transform := transformBot
      )
    )
  }

  def OpenCard(card: Card, x: Int, y: Int, w: Double = 44.5, h: Double = 62): SvgElement =
    svg.g(
      svg.rect(
        svg.x := s"${x + 0.5}",
        svg.y := s"${y + 0.5}",
        svg.rx := "2.5",
        svg.ry := "2.5",
        svg.width := s"$w",
        svg.height := s"$h",
        svg.style := "fill:#FFF;stroke-width:1;stroke:#000"
      ),
      svg.rect(
        svg.x := s"${x + 3}",
        svg.y := s"${y + 3}",
        svg.rx := "2",
        svg.ry := "2",
        svg.width := s"${w-5}",
        svg.height := s"${h-5}",
        svg.fill := "#FFF"
      ),
      svg.text(
        svg.fontFamily := "DejaVu Sans, sans-serif",
        svg.fontWeight := "bold",
        svg.fontSize := "20",
        svg.fill := (if (card.isRed) "red" else "black"),
        svg.x := s"${x + w / 9}",
        svg.y := s"${y + h / 2 - 1}",
        card.show
      )
    )

  def Board(cards: List[Card]): SvgElement =
    svg.g(
      svg.idAttr := "Board",
      cards.zipWithIndex.map { case (card, i) => OpenCard(card, 270 + i * 50, 230) }
    )

  def Pot(pot: Pot): SvgElement =
    svg.g(
      svg.rect(
        svg.x := "600",
        svg.y := "230",
        svg.rx := "3",
        svg.ry := "3",
        svg.width := "80",
        svg.height := "62",
        svg.fill := "#E9C891"
      ),
      svg.text(
        svg.x := "610",
        svg.y := "250",
        svg.fontSize := "18",
        svg.fontWeight := "bold",
        "Pot:"
      ),
      svg.text(
        svg.x := "620",
        svg.y := "280",
        svg.fontSize := "20",
        svg.fontWeight := "bold",
        pot.playerBets.values.sum.toString
      )
    )

  def PlayerAvatar(x: Double, y: Double, sx: Double, sy: Double): SvgElement = {
    // 0 0 1000 1000
    svg.g(
      svg.transform := s"translate($x,$y) scale($sx,$sy)",
      svg.path(
        svg.d := "M4679.4,4981c-383.1-69.5-821.7-321.5-1184.9-682.8c-383.1-379.1-589.5-786-657-1284.2c-25.8-200.5-13.9-400.9,41.7-637.1c21.8-91.3,39.7-168.7,39.7-172.7c0-4-29.8-13.9-63.5-19.9c-232.2-49.6-256-363.2-71.4-942.8c33.7-103.2,83.4-232.2,109.2-287.8c55.6-109.2,158.8-228.3,198.5-228.3c13.9,0,29.8-23.8,37.7-53.6c117.1-547.8,248.1-851.5,486.3-1137.3c363.2-436.7,1000.3-690.7,1546.2-619.3c817.7,109.2,1355.6,647,1558.1,1558.1c29.8,137,37.7,152.8,73.4,140.9c133-41.7,264,138.9,393,537.9c150.8,476.3,158.8,791.9,17.9,891.2l-45.7,31.8l33.8,202.5c137,813.8,71.5,1389.4-190.5,1720.8c-83.4,105.2-228.3,208.4-335.4,240.2c-67.5,17.9-99.2,43.7-158.8,129c-194.5,277.9-633.2,535.9-1046,613.3C5258.9,5018.7,4891.7,5020.7,4679.4,4981z"
      ),
      svg.path(
        svg.d := "M3226.5-985.3c-262-162.7-813.8-436.6-1101.6-547.8c-115.1-43.7-347.3-123.1-516-172.7c-363.2-109.2-474.4-168.7-623.2-329.5c-176.6-188.6-287.8-462.5-327.5-801.8l-17.9-158.8l111.1-105.2c954.7-895.2,2062.2-1433,3368.2-1635.5c375.1-57.5,1149.2-71.4,1538.2-27.8c726.4,83.3,1369.5,266,1998.7,567.6c597.4,285.8,1036.1,583.5,1548.1,1048c154.8,140.9,160.8,148.9,152.8,218.3c-65.5,502.1-170.7,754.2-402.9,960.7c-133,121.1-254,176.6-551.8,262c-561.7,160.8-1109.5,406.9-1647.4,738.3l-202.4,125l-19.9-63.5c-657-2086-883.2-2792.6-915-2848.2c-7.9-15.9-89.3,192.5-194.5,492.2l-182.6,518l47.6,63.5c158.8,216.4,309.6,565.7,309.6,722.5c0,208.4-121.1,351.3-347.3,412.8c-190.5,51.6-466.4,33.7-615.3-39.7c-317.6-154.8-299.7-535.9,47.6-1050l77.4-115.1l-45.6-123.1c-23.8-65.5-105.2-297.7-180.6-512.1c-75.4-216.3-140.9-393-146.9-393c-6,0-206.4,619.3-444.6,1375.4c-240.2,754.2-448.6,1415.2-466.4,1464.8l-31.8,91.3L3226.5-985.3z"
      )
    )
  }

  def PlayerInfo(
    player: PokerPlayer,
    isShown: Boolean = false,
    bet: Token = 0,
    isDealer: Boolean = false,
    isHighlighted: Boolean = false
  ): SvgElement = {
    val (x,y) = player.seat match {
      case 0 => (500, 490)
      case 1 => (220, 490)
      case 2 => (5, 420)
      case 3 => (5, 130)
      case 4 => (220, 10)
      case 5 => (400, 10)
      case 6 => (630, 10)
      case 7 => (850, 130)
      case 8 => (850, 420)
    }

    val playerCards =
      svg.g(player.hand.cards.zipWithIndex.map { case (card, i) => OpenCard(card, x + 130 + i * 50, y) })

    svg.g(
      svg.rect(
        svg.x := s"$x",
        svg.y := s"$y",
        svg.rx := "3",
        svg.ry := "3",
        svg.width := "130",
        svg.height := "60",
        svg.fill := (if (isHighlighted) "#ff4f12" else "#D06224")
      ),
      PlayerAvatar(x, y+30, 0.005, -0.005),
      svg.text(
        svg.x := s"${x+55}",
        svg.y := s"${y+15}",
        svg.fontSize := "15",
        svg.fontWeight := "bold",
        player.name.toString
      ),
      svg.text(
        svg.x := s"${x+55}",
        svg.y := s"${y+35}",
        svg.fontSize := "15",
        svg.fontWeight := "bold",
        player.tokens.toString
      ),
      svg.text(
        svg.x := s"${x+55}",
        svg.y := s"${y+55}",
        svg.fontSize := "15",
        svg.fontWeight := "bold",
        bet.toString
      ),
      if (player.hasCards) CardsPair(player.seat) else svg.g(),  //sfcards on table
      if (player.hasCards && isShown) playerCards else svg.g(),
      if (bet > 0) ChipPair(player.seat) else svg.g(),
      if (isDealer) Svg.DealerButton(player.seat) else svg.g()
    )
  }

  def PlayerCards(cards: List[Card]): SvgElement =
    svg.svg(
      svg.x := "0px", svg.y := "0px",
      svg.width := "300px", svg.height := "150px",
      svg.viewBox := "0 0 300 150",
      cards.zipWithIndex.map { case (card, i) => OpenCard(card, x = 40 + 70*i, y = 30, w = 60, h = 100)}
    )

}
