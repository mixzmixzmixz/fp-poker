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
import mixzpoker.components.Dialogs._
import mixzpoker.domain.User.UserDto.UserDto
import mixzpoker.{Config, Page}
import mixzpoker.domain.lobby.LobbyDto.LobbyDto
import mixzpoker.domain.lobby.{LobbyInputMessage, LobbyOutputMessage}
import mixzpoker.domain.lobby.LobbyOutputMessage._
import mixzpoker.domain.lobby.LobbyInputMessage._

object LobbyPage {
  case class ChatState(messages: List[(UserDto, String)] = List.empty) {
    def addMessage(user: UserDto, message: String): ChatState = copy(messages = (user, message) :: messages)
  }

  object requests {
    def getLobbyRequest(name: String)(implicit token: String): EventStream[Try[LobbyDto]] =
      Fetch.get(
        url = s"${Config.rootEndpoint}/lobby/$name",
        headers = Map("Authorization" -> token)
      ).decodeOkay[LobbyDto].recoverToTry.map(_.map(_.data))

  }

  import requests._

  def createWS(name: String): WebSocket[LobbyOutputMessage, LobbyInputMessage] = WebSocket
    .url(s"${Config.wsRootEndpoint}/lobby/$name/ws")
    .receiveText[LobbyOutputMessage](decode[LobbyOutputMessage])
    .sendText[LobbyInputMessage](_.asJson.noSpaces)
    .build(reconnectRetries = Int.MaxValue, reconnectDelay = 3.seconds)

  def apply($lobbyPage: Signal[Page.Lobby])(implicit token: String): HtmlElement = {
    val $lobby = $lobbyPage.flatMap(l => getLobbyRequest(l.name)).map(_.fold(ExceptionPage.apply, renderLobby))

    div(child <-- $lobby, width("100%"))
  }

  def controlButtons()(implicit token: String): HtmlElement = {
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
    if (lobby.users.isEmpty)
      div("No Users!")
    else
      MList(
        _.slots.default(
          lobby.users.map { player =>
            MList.ListItem(
              _.`graphic` := "avatar",
              _.`twoline` := true,
              _.`hasMeta` := true,
              _.slots.graphic(Icon().amend(span("person"))),
              _.slots.default(span(player.user.name)),
              _.slots.secondary(span(player.buyIn.toString)),
            )
          }: _*
        )
      )
  }

  private def renderLobby(lobbyInit: LobbyDto)(implicit token: String): HtmlElement = {
    val ws = createWS(lobbyInit.name)
    val lobbyVar = Var[LobbyDto](lobbyInit)
    val isSettingsDialogOpen = Var(false)
    val isJoinLobbyDialogOpen = Var(false)
    val errMsg = Var("")
    val chatState = Var(ChatState())

    def processServerMessages(message: LobbyOutputMessage): Unit = {
      dom.console.log(s"receive a message from server: ${message.toString}")
      message match {
        case KeepAlive                      => ()
        case LobbyState(lobby)              => lobbyVar.set(lobby)
        case ChatMessageFrom(message, user) => chatState.update(_.addMessage(user, message))
      }
    }

    div(
      ws.connect,
      ws.connected --> { _ =>
        dom.console.log("ws connected")
        ws.sendOne(Register(token))
      },
      ws.received --> { message => processServerMessages(message)},
      flexDirection.column, width("100%"), height("100%"),
      div(
        cls("lobby-heading"),
        span(child.text <-- lobbyVar.signal.map(_.name), cls("lobby-heading-name")),
        child <-- lobbyVar.signal.map(lobby => SettingsDialog(isSettingsDialogOpen, lobby.gameSettings)),
        child <-- lobbyVar.signal.map { lobby =>
          JoinLobbyDialog(isJoinLobbyDialogOpen, lobby, ws)
        },
        ErrorDialog(errMsg),
        div(
          float("right"), marginRight("0"), marginLeft("auto"),
          Button(
            _.`raised` := true,
            _.`label` := "join",
            _ => cls("lobby-heading-btn"),
            _ => onClick --> { _ => isJoinLobbyDialogOpen.set(true)}
          ), //todo or leave
          Button(
            _.`raised` := true,
            _.`label` := "settings",
            _ => cls("lobby-heading-btn"),
            _ => onClick --> { _ => isSettingsDialogOpen.set(true)}
          )
        )
      ),
      div(
        cls("mixz-container"),
        flexDirection.row,
        div(
          cls("lobby-main")
        ),
        div(
          cls("lobby-users"),
          child <-- lobbyVar.signal.map(Users)
        )
      )

    )
  }
}
