package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import io.circe.syntax._
import io.circe.parser.decode
import laminar.webcomponents.material.Button
import org.scalajs.dom

import scala.concurrent.duration._
import mixzpoker.{AppContext, AppError, Config, Page}
import mixzpoker.components.{Navigation, Svg}
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.core.{Card, Rank, Suit}
import mixzpoker.domain.game.poker.{PokerEvent, PokerGame, PokerOutputMessage, PokerPlayer}
import mixzpoker.domain.game.poker.PokerOutputMessage._
import mixzpoker.pages.LobbyPage.ChatState


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
      val chatState = Var(ChatState())
      val gameState = Var[PokerGame](PokerGame.empty(GameId.fromString(gameId).toOption.get)) //todo deal with gameID
      val $me       = gameState.signal.combineWith(appContext.signal).map { case (g, ac) => g.players.get(ac.user.id) }

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

      def PlayerSelfActions(): HtmlElement = {
        div(
          cls("poker-game-player-actions"),
          div(
            cls("top-row"),
            Button(_.`label` := "Fold", _.`raised` := true),
            Button(_.`label` := "Call", _.`raised` := true),
            Button(_.`label` := "Check", _.`raised` := true),
          ),
          Button(_.`label` := "Raise", _.`raised` := true),
          Button(_.`label` := "AllIn", _.`raised` := true),
        )
      }

      def PokerMidArea(game: PokerGame): HtmlElement = {
        div(
          cls("poker-game-mid"),
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

      def PokerBotArea() = child <-- $me.map {
        case Some(me) =>
          div(
            cls("poker-game-bot"),
            PlayerSelfInfo(me),
            PlayerSelfCards(me),
            PlayerSelfActions(),
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
          child <-- gameState.signal.map(PokerMidArea),
          PokerBotArea(),
        )
      )
    }

    div(width := "100%", child <-- $gamePage.map(p => renderGamePage(p.id)))
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = Navigation.DefaultTopButtons()
}
