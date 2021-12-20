package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import io.circe.syntax._
import io.circe.parser.decode
import org.scalajs.dom

import scala.concurrent.duration._
import laminar.webcomponents.material.{Button, Slider, Textfield}
import mixzpoker.components.Dialogs.JoinDialog
import mixzpoker.{AppContext, AppError, Config, Page}
import mixzpoker.components.{Chat, Navigation, Svg}
import mixzpoker.domain.game.GameId
import mixzpoker.domain.game.poker.{PokerEvent, PokerGame, PokerOutputMessage, PokerPlayer}
import mixzpoker.domain.game.poker.PokerOutputMessage._
import mixzpoker.domain.game.poker.PokerEvent._


object PokerGamePage {

  def createWS(name: String): WebSocket[PokerOutputMessage, PokerEvent] = WebSocket
    .url(s"${Config.wsRootEndpoint}/poker/$name/ws")
    .receiveText[PokerOutputMessage](decode[PokerOutputMessage])
    .sendText[PokerEvent](_.asJson.noSpaces)
    .build(reconnectRetries = 5, reconnectDelay = 3.seconds)

  def apply($gamePage: Signal[Page.PokerGame])(implicit appContext: Var[AppContext]): HtmlElement = {
    def renderGamePage(gameId: String): HtmlElement = {
      val ws        = createWS(gameId)
      val gameState = Var[PokerGame](PokerGame.empty(GameId.fromString(gameId).toOption.get)) //todo deal with gameID
      val $me       = gameState.signal.combineWith(appContext.signal).map { case (g, ac) => g.players.get(ac.user.id) }
      val (chatState, chatArea) = Chat.create(
        s"${Config.wsRootEndpoint}/poker/$gameId/chat/ws",
        "poker-chat-area"
      )
      val gameStateAnnounce = Var[String]("")

      def processServerMessages(message: PokerOutputMessage): Unit = {
        message match {
          case ErrorMessage(_, message) =>
            appContext.now().error.set(AppError.GeneralError(message))

          case GameState(game) =>
            dom.console.log(game.asJson.spaces2)
            gameState.set(game)
            game.showdown.fold(()) { showdown =>
              chatState.update(_.addLogMessage("Showdown!"))
              showdown.combs.foreach { ls =>
                chatState.update(_.addLogMessage(ls.map { case (c, p) => s"${p.name} -> $c"}.mkString(", ")))
              }
            }

          case PlayerToAction(id, secondsForAction) =>
            gameState.now().players.get(id).fold {
              chatState.update(_.addLogMessage(s"Error! No player with id $id"))
            } { p =>
              chatState.update(_.addLogMessage(s"It's time for ${p.name} to act!"))
            }

          case LogMessage(message) =>
            chatState.update(_.addLogMessage(message))

        }
      }


      //VIEWS

      def PlayerSelfInfo(me: PokerPlayer): HtmlElement = {
        div(
          cls("poker-game-player-self"),
          div(cls("poker-game-player-name"), child.text <-- appContext.signal.map(_.user.name.toString)),
          div(cls("poker-game-player-balance"), s"${me.tokens} tokens"),
          div(cls("poker-game-player-state"), s"${me.state.toString}")
        )
      }

      def PlayerSelfCards(me: PokerPlayer): HtmlElement = {
        div(
          cls("poker-game-player-self-sfcards"),
          Svg.PlayerCards(me.hand.cards)
        )
      }

      def PlayerSelfActions(me: PokerPlayer): HtmlElement = {
        val betAmount = Var(Math.min(gameState.now().pot.minBet, me.tokens))

        val allInBtn = Button(
          _.`label` := "AllIn",
          _.`raised` := true,
          _.`disabled` <-- gameState.signal.map(!_.canPlayerAllIn(me)),
          _ => onClick --> { _ => ws.sendOne(AllIn) }
        )

        val raiseBtn = Button(
          _.`label` := "Raise",
          _.`raised` := true,
          _.`disabled` <-- gameState.signal.combineWith(betAmount.signal).map { case (gs, bet) =>
            !gs.canPlayerRaise(me, bet)
          },
          _ => onClick --> { _ =>
            ws.sendOne(Raise(betAmount.now()))
            betAmount.set(Math.min(gameState.now().pot.minBet, me.tokens))
          }
        )

        div(
          cls("poker-game-player-actions"),
          div(
            cls("top-row"),
            Button(
              _.`label` := "Fold",
              _.`raised` := true,
              _.`disabled` <-- gameState.signal.map(!_.canPlayerFold(me)),
              _ => onClick --> { _ => ws.sendOne(Fold) }
            ),
            Button(
              _.`label` := "Call",
              _.`raised` := true,
              _.`disabled` <-- gameState.signal.map(!_.canPlayerCall(me)),
              _ => onClick --> { _ => ws.sendOne(Call) }
            ),
            Button(
              _.`label` := "Check",
              _.`raised` := true,
              _.`disabled` <-- gameState.signal.map(!_.canPlayerCheck(me)),
              _ => onClick --> { _ => ws.sendOne(Check) }
            ),
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
            Svg.Pot(game.pot),
            Svg.Board(game.board),
            game.players.values.map(p =>
              Svg.PlayerInfo(
                p,
                isShown = p.userId == appContext.now().user.id || game.showdown.isDefined,
                bet = game.pot.playerBetsThisRound.getOrElse(p.userId, 0),
                isDealer = p.seat == game.dealerSeat,
                isHighlighted = p.seat == game.playerToActSeat
              )
            ).toList

          )
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

      def PokerTopArea(): HtmlElement = {
        val isJoinDialogOpen = Var(false)

        val joinBtn = Button(
          _.`raised` := true,
          _.`label` := "join",
          _ => cls("poker-game-heading-btn"),
          _ => onClick --> { _ => isJoinDialogOpen.set(true) }
        )

        val leaveBtn = Button(
          _.`raised` := true,
          _.`label` := "leave",
          _ => cls("poker-game-heading-btn"),
          _ => onClick --> { _ => ws.sendOne(Leave) }
        )

        div(
          cls("poker-game-top"),
          h1(cls("poker-game-heading"), s"$gameId"),
          div(
            cls("poker-game-controls"),
            child <-- $me.map {
              case Some(me) => leaveBtn
              case None     => joinBtn
            }
          ),
          child <-- gameState.signal.map { gs =>
            JoinDialog(
              isJoinDialogOpen,
              "JoinGame", gs.settings.buyInMin,
              (buyIn: Int) => ws.sendOne(Join(buyIn, appContext.now().user.name))
            )
          }
        )
      }

      div(
        cls("poker-game-container"),
        ws.connect,
        ws.connected --> { _ws =>
          dom.console.log("ws connected")
          _ws.send(appContext.now().token)
          ws.sendOne(PokerEvent.Ping)
        },
        ws.received --> { message => processServerMessages(message) },
        div(
          cls("poker-game-main"),
          PokerTopArea(),
          child <-- gameState.signal.map(PokerGamePlayArea),
          PokerBotArea(),
        ),
        div(
          cls("poker-game-right"),
          div(
            cls("poker-game-announcement"),
            child.text <-- gameStateAnnounce.signal
          ),
          chatArea
        )

      )
    }

    div(width := "100%", child <-- $gamePage.map(p => renderGamePage(p.id)))
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = Navigation.DefaultTopButtons()
}
