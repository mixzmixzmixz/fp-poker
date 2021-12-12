package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import io.circe.syntax._
import io.circe.parser.decode
import org.scalajs.dom
import scala.concurrent.duration._

import laminar.webcomponents.material.{Button, Slider, Textfield}
import mixzpoker.{AppContext, AppError, Config, Page}
import mixzpoker.components.{Navigation, Svg, Chat}
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.{Card, Rank, Suit}
import mixzpoker.domain.game.poker.{PokerEvent, PokerGame, PokerOutputMessage, PokerPlayer}
import mixzpoker.domain.game.poker.PokerOutputMessage._


object PokerGamePage {
  val board: List[Card] = List(
    Card(Rank.Ten, Suit.Clubs),
    Card(Rank.Ace, Suit.Hearts),
    Card(Rank.King, Suit.Diamonds),
    Card(Rank.Queen, Suit.Spades),
    Card(Rank.Two, Suit.Clubs),
  )


  def createWS(name: String): WebSocket[PokerOutputMessage, PokerEvent] = WebSocket
    .url(s"${Config.wsRootEndpoint}/poker/$name/ws")
    .receiveText[PokerOutputMessage](decode[PokerOutputMessage])
    .sendText[PokerEvent](_.asJson.noSpaces)
    .build(reconnectRetries = 5, reconnectDelay = 3.seconds)

  def apply($gamePage: Signal[Page.PokerGame])(implicit appContext: Var[AppContext]): HtmlElement = {
    def renderGamePage(gameId: String): HtmlElement = {

      //STATE

      val ws        = createWS(gameId)
      val gameState = Var[PokerGame](PokerGame.empty(GameId.fromString(gameId).toOption.get)) //todo deal with gameID
      val $me       = gameState.signal.combineWith(appContext.signal).map { case (g, ac) => g.players.get(ac.user.id) }

      val (chatState, chatArea) = Chat.create(
        s"${Config.wsRootEndpoint}/poker/$gameId/chat/ws",
        "poker-chat-area"
      )
      def processServerMessages(message: PokerOutputMessage): Unit = {
        dom.console.log(s"receive a message from server: ${message.toString}")
        message match {
          case ErrorMessage(message) => appContext.now().error.set(AppError.GeneralError(message))
          case GameState(game)       =>
            dom.console.log(game.asJson.spaces2)
            gameState.set(game)
        }
      }


      //VIEWS

      def PlayerSelfInfo(me: PokerPlayer): HtmlElement = {
        div(
          cls("poker-game-player-self"),
          div(cls("poker-game-player-name"), child.text <-- appContext.signal.map(_.user.name)),
          div(cls("poker-game-player-balance"), s"${me.tokens} tokens"),
          div(cls("poker-game-player-state"), s"${me.state.toString}")
        )
      }

      def PlayerSelfCards(me: PokerPlayer): HtmlElement = {
        div(
          cls("poker-game-player-self-cards"),
          Svg.PlayerCards(me.hand.cards)
        )
      }

      def PlayerSelfActions(me: PokerPlayer): HtmlElement = {
        val betAmount = Var(Math.min(gameState.now().pot.minBet, me.tokens))

        val allInBtn = Button(_.`label` := "AllIn", _.`raised` := true)
        val raiseBtn = Button(_.`label` := "Raise", _.`raised` := true)

        div(
          cls("poker-game-player-actions"),
          div(
            cls("top-row"),
            Button(_.`label` := "Fold", _.`raised` := true),
            Button(_.`label` := "Call", _.`raised` := true),
            Button(_.`label` := "Check", _.`raised` := true),
          ),
          div(
            cls("bot-row"),
            child <-- betAmount.signal.map(_ == me.tokens).map {
              case true  => allInBtn
              case false => raiseBtn
            },
            Slider(
              _ => cls("bet-slide"),
              _.`min` <-- gameState.signal.map(gs => Math.min(gs.pot.minBet, me.tokens)),
              _.`max` := me.tokens,
              _.`value` <-- betAmount.signal.map(_.toDouble),
              slider => inContext { thisNode =>
                slider.onInput.mapTo(thisNode.ref.value.toInt) --> betAmount
              }
            ),
            // todo validate max and min numbers
            Textfield(
              _ => cls("bet-text"),
              _.`value` <-- betAmount.signal.map(_.toString),
              _.`type` := "number",
              _.`outlined` := true,
              _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`.toInt) --> betAmount}
            )
          )
        )
      }

      def PokerGamePlayArea(game: PokerGame): HtmlElement = {
        div(
          cls("poker-game-play-area"),
          svg.svg(
            svg.x := "0px", svg.y := "0px",
            svg.width := "957.2px", svg.height := "545px",
            svg.viewBox := "1.4 8 957.2 545",
            Svg.CardSymbol(),
            Svg.ChipSymbol(),
            Svg.Table(),
            Svg.DealerButton(1),
            Svg.ChipSingle(1),
            Svg.ChipPair(1),
            Svg.Board(board),
            game.players.values.map(p => Svg.PlayerInfo(p, isShown = p.userId == appContext.now().user.id)).toList

          )
        )
      }

      def PokerMidArea(game: PokerGame): HtmlElement = {
        div(
          cls("poker-game-mid"),
          PokerGamePlayArea(game),
        )
      }

      def PokerBotArea() = child <-- $me.map {
        case Some(me) =>
          div(
            cls("poker-game-bot"),
            PlayerSelfInfo(me),
            PlayerSelfCards(me),
            PlayerSelfActions(me),
          )
        case None => div()
      }


      div(
        cls("poker-game-container"),
        ws.connect,
        ws.connected --> { _ws =>
          dom.console.log("ws connected")
          _ws.send(appContext.now().token)
          ws.sendOne(PokerEvent.Ping)
        },
        ws.received --> { message => processServerMessages(message)},
        div(
          cls("poker-game-main"),
          div(cls("poker-game-top"), "PokerGameTop!"),
          child <-- gameState.signal.map(PokerGamePlayArea),
          PokerBotArea(),
        ),
        div(
          cls("poker-game-right"),
          chatArea
        )

      )
    }

    div(width := "100%", child <-- $gamePage.map(p => renderGamePage(p.id)))
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = Navigation.DefaultTopButtons()
}
