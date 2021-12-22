package mixzpoker.pages

import com.raquo.laminar.api.L._
import com.raquo.laminar.api.L.span
import io.laminext.fetch.circe._
import io.laminext.websocket.WebSocket
import io.circe.syntax._
import io.circe.parser.decode
import org.scalajs.dom

import scala.concurrent.duration._
import scala.util.Try
import laminar.webcomponents.material.{Button, Icon, List => MList}
import mixzpoker.components.Chat
import mixzpoker.components.Dialogs._
import mixzpoker.domain.game.GameSettings
import mixzpoker.{App, AppContext, AppError, Config, Page}
import mixzpoker.domain.lobby.{Lobby, LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._

object LobbyPage {

  object requests {
    def getLobbyRequest(name: String)(implicit appContext: Var[AppContext]): EventStream[Try[Lobby]] =
      Fetch.get(
        url = s"${Config.rootEndpoint}/lobby/$name",
        headers = Map("Authorization" -> appContext.now().token)
      ).decodeOkay[Lobby].recoverToTry.map(_.map(_.data))
  }

  import requests._

  def createWS(name: String): WebSocket[LobbyOutputMessage, LobbyInputMessage] = WebSocket
    .url(s"${Config.wsRootEndpoint}/lobby/$name/ws")
    .receiveText[LobbyOutputMessage](decode[LobbyOutputMessage])
    .sendText[LobbyInputMessage](_.asJson.noSpaces)
    .build(reconnectRetries = 5, reconnectDelay = 3.seconds)

  def apply($lobbyPage: Signal[Page.Lobby])(implicit appContext: Var[AppContext]): HtmlElement = {
    def renderLobby(lobbyInit: Lobby): HtmlElement = {
      val ws                    = createWS(lobbyInit.name.toString)
      val lobbyVar              = Var[Lobby](lobbyInit)
      val isSettingsDialogOpen  = Var(false)
      val isJoinLobbyDialogOpen = Var(false)
      val $me                   = lobbyVar.signal.combineWith(appContext.signal.map(_.user)).map {
                                    case (l, u) => l.players.find(_.user.name == u.name)
                                  }
      val (chatState, chatArea) = Chat.create(
                                    s"${Config.wsRootEndpoint}/lobby/${lobbyInit.name}/chat/ws",
                                    "lobby-chat-area"
                                  )

      def processServerMessages(message: LobbyOutputMessage): Unit = {
        dom.console.log(s"receive a message from server: ${message.toString}")
        message match {
          case KeepAlive             => ()
          case LobbyState(lobby)     => lobbyVar.set(lobby)
          case ErrorMessage(message) => appContext.now().error.set(AppError.GeneralError(message))
          case GameStarted(gameId)   => appContext.now().error.set(AppError.GeneralError(s"PokerGame have begun! $gameId"))
        }
      }

      val joinBtn = Button(
        _.`raised` := true,
        _.`label` := "join",
        _ => cls("lobby-heading-btn"),
        _ => onClick --> { _ => isJoinLobbyDialogOpen.set(true) }
      )

      val leaveBtn = Button(
        _.`raised` := true,
        _.`label` := "leave",
        _ => cls("lobby-heading-btn"),
        _ => onClick --> { _ => ws.sendOne(Leave) }
      )

      def MainArea($settings: Signal[GameSettings]): HtmlElement = {
        div(
          cls("lobby-game-area"),
          p(
            cls := "lobby-game-area-text-line",
            child.text <-- lobbyVar.signal.map(l => s"PokerGame: ${l.gameType.toString}")
          ),
          p(
            cls := "lobby-game-area-text-line",
            child.text <-- $settings.map(s => s"Min Buy In: ${s.buyInMin}")
          ),
          p(
            cls := "lobby-game-area-text-line",
            child.text <-- $settings.map(s => s"Max Buy In: ${if (s.buyInMax != Int.MaxValue) s.buyInMax.toString else "No Limit"}")
          ),
          p(
            cls <-- lobbyVar.signal.map { l =>
              if (l.players.size >= l.gameSettings.minPlayers && l.players.size <= l.gameSettings.maxPlayers)
                "lobby-game-area-text-line"
              else
                "lobby-game-area-text-line-invalid"
            },
            child.text <-- lobbyVar.signal.map { l => s"Players: ${l.players.size} / ${l.gameSettings.maxPlayers}"}
          ),
        )
      }

      def GameStartProcess(gameId: String) = {
        App.router.pushState(Page.PokerGame(gameId))
        div("Game has started!")
      }

      div(
        ws.connect,
        ws.connected --> { _ws =>
          dom.console.log("ws connected")
          _ws.send(appContext.now().token)
        },
        ws.received --> { message => processServerMessages(message)},
        cls("lobby-container"),
        div(
          cls("lobby-heading"),
          span(child.text <-- lobbyVar.signal.map(_.name.toString), cls("lobby-heading-name")),
          child <-- lobbyVar.signal.map { lobby =>
            SettingsDialog(isSettingsDialogOpen, lobby.gameSettings)
          },
          child <-- lobbyVar.signal.map { lobby =>
            JoinDialog(
              isJoinLobbyDialogOpen,
              s"Join Lobby ${lobby.name}", lobby.gameSettings.buyInMin,
              (buyIn: Int) => ws.sendOne(LobbyInputMessage.Join(buyIn))
            )
          },
          div(
            cls("lobby-heading-btns"),
            child <-- $me.map {
              case Some(_) => leaveBtn
              case None    => joinBtn
            },
            Button(
              _.`raised` := true,
              _.`label` := "settings",
              _ => cls("lobby-heading-btn"),
              _ => onClick --> { _ => isSettingsDialogOpen.set(true)}
            ),
            child <-- $me.map {
              case Some(p) => Button(
                _.`raised` := true,
                _.`label` := (if (p.ready) "ready!" else "ready?"),
                _.`dense` := p.ready,
                _ => cls("lobby-heading-btn"),
                _ => onClick --> { _ => ws.sendOne(if (p.ready) NotReady else Ready)}
              )
              case None => div()
            }

          )
        ),
        div(cls("mixz-container"), flexDirection.row,
          div(cls("lobby-main"), flexDirection.column,
            child <-- lobbyVar.signal.map {
              _.gameId.fold(MainArea(lobbyVar.signal.map(_.gameSettings)))(gid => GameStartProcess(gid.toString))
            },
            chatArea
          ),
          div(cls("lobby-players"), child <-- lobbyVar.signal.map(Users))
        )
      )
    }

    val $lobby = $lobbyPage.flatMap(l => getLobbyRequest(l.name)).map(
      _.fold(ExceptionPage.apply, l => l.gameId match {
        case Some(gameId) =>
          App.router.pushState(Page.PokerGame(gameId.toString))
          div()
        case None => renderLobby(l)
      })
    )

    div(child <-- $lobby, width("100%"))
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = {
    div(
      Button(
        _.`raised` := true,
        _.slots.icon(span("🚀")),
        _.`label` := "Button 1"
      ),
      Button(
        _.`raised` := true,
        _.slots.icon(span("🚀")),
        _.`label` := "Button 2"
      )
    )
  }

  def Users(lobby: Lobby): HtmlElement = {
    if (lobby.players.isEmpty)
      div("No Users!")
    else
      MList(
        _.slots.default(
          lobby.players.map { player =>
            MList.ListItem(
              _ => cls("lobby-player"),
              _.`graphic` := "avatar",
              _.`twoline` := true,
              _.`hasMeta` := true,
              _.slots.graphic(Icon().amend(span("person"))),
              _.slots.default(span(player.user.name.toString)),
              _.slots.secondary(span(s"${player.buyIn}  - ${if (player.ready) "ready" else "not ready"}")),
            )
          }: _*
        )
      )
  }
}
