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
import laminar.webcomponents.material.{Button, Fab, Icon, Textarea, Textfield, List => MList}
import mixzpoker.components.Dialogs._
import mixzpoker.domain.game.GameSettings
import mixzpoker.domain.user.UserDto.UserDto
import mixzpoker.{AppContext, AppError, Config, Page}
import mixzpoker.domain.lobby.LobbyDto.LobbyDto
import mixzpoker.domain.lobby.{LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._

object LobbyPage {
  case class ChatState(messages: List[(UserDto, String)] = List.empty) {
    def addMessage(user: UserDto, message: String): ChatState = copy(messages = (user, message) :: messages)
  }

  object requests {
    def getLobbyRequest(name: String)(implicit appContext: Var[AppContext]): EventStream[Try[LobbyDto]] =
      Fetch.get(
        url = s"${Config.rootEndpoint}/lobby/$name",
        headers = Map("Authorization" -> appContext.now().token)
      ).decodeOkay[LobbyDto].recoverToTry.map(_.map(_.data))
  }

  import requests._

  def createWS(name: String): WebSocket[LobbyOutputMessage, LobbyInputMessage] = WebSocket
    .url(s"${Config.wsRootEndpoint}/lobby/$name/ws")
    .receiveText[LobbyOutputMessage](decode[LobbyOutputMessage])
    .sendText[LobbyInputMessage](_.asJson.noSpaces)
    .build(reconnectRetries = 5, reconnectDelay = 3.seconds)

  def apply($lobbyPage: Signal[Page.Lobby])(implicit appContext: Var[AppContext]): HtmlElement = {
    def renderLobby(lobbyInit: LobbyDto): HtmlElement = {
      val ws                    = createWS(lobbyInit.name)
      val lobbyVar              = Var[LobbyDto](lobbyInit)
      val isSettingsDialogOpen  = Var(false)
      val isJoinLobbyDialogOpen = Var(false)
      val chatState             = Var(ChatState())
      val $me                   = lobbyVar.signal.combineWith(appContext.signal.map(_.user)).map {
                                    case (l, u) => l.players.find(_.user.name == u.name)
                                  }

      def processServerMessages(message: LobbyOutputMessage): Unit = {
        dom.console.log(s"receive a message from server: ${message.toString}")
        message match {
          case KeepAlive                      => ()
          case LobbyState(lobby)              => lobbyVar.set(lobby)
          case ChatMessageFrom(message, user) => chatState.update(_.addMessage(user, message))
          case ErrorMessage(message)          => appContext.now().error.set(AppError.GeneralError(message))
          case GameStarted(gameId)            => appContext.now().error.set(AppError.GeneralError(s"Game have begun! $gameId"))
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

      val textarea = Textarea(
        _ => cls("lobby-chat-messages"),
        _.`value` <-- chatState.signal.map(
          _.messages.map { case (user, msg) => s"${user.name}: $msg"}.reverse.mkString("\n")
        ),
        _.`disabled` := true,
        _.`rows` := 8, _.`cols` := 130
      )

      def ChatArea(): HtmlElement = {
        val message = Var("")
        div(
          cls("lobby-chat-area"), flexDirection.column,
          textarea,
          div(
            cls("lobby-chat-buttons"), flexDirection.row,
            Textfield(
              _ => cls("lobby-chat-send-field"),
              _.`value` <-- message,
              _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> message },
              _.`outlined` := true,
              _.`charCounter` := true,
              _.`maxLength` := 1000,
              _ => onKeyPress.filter(e => (e.keyCode == dom.KeyCode.Enter) && message.now().nonEmpty) --> { _ =>
                ws.sendOne(ChatMessage(message.now()))
                message.set("")
              }
            ),
            Fab(
              _.`icon` := "send",
              _ => onClick.filter(_ => message.now().nonEmpty) --> { _ =>
                ws.sendOne(ChatMessage(message.now()))
                message.set("")
              }
            )
          )
        )
      }

      def MainArea($settings: Signal[GameSettings]): HtmlElement = {
        div(
          cls("lobby-game-area"),
          p(
            cls := "lobby-game-area-text-line",
            child.text <-- lobbyVar.signal.map(l => s"Game: ${l.gameType.toString}")
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

      def GameStartProcess() = {
        div(
          cls("lobby-game-area"),
          p(
            cls := "lobby-game-area-text-line",
            "Everybody is ready! Game is about to start!"
          )
        )
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
          span(child.text <-- lobbyVar.signal.map(_.name), cls("lobby-heading-name")),
          child <-- lobbyVar.signal.map { lobby =>
            SettingsDialog(isSettingsDialogOpen, lobby.gameSettings)
          },
          child <-- lobbyVar.signal.map { lobby =>
            JoinLobbyDialog(isJoinLobbyDialogOpen, lobby, ws)
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
            child <-- lobbyVar.signal.map { l =>
              if (l.gameId.isDefined)
                GameStartProcess()
              else
                MainArea(lobbyVar.signal.map(_.gameSettings))
            },
            ChatArea()
          ),
          div(cls("lobby-players"), child <-- lobbyVar.signal.map(Users))
        )
      )
    }

    val $lobby = $lobbyPage.flatMap(l => getLobbyRequest(l.name)).map(_.fold(ExceptionPage.apply, renderLobby))

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

  def Users(lobby: LobbyDto): HtmlElement = {
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
              _.slots.default(span(player.user.name)),
              _.slots.secondary(span(s"${player.buyIn}  - ${if (player.ready) "ready" else "not ready"}")),
            )
          }: _*
        )
      )
  }
}
