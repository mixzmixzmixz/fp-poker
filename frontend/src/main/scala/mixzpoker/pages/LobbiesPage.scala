package mixzpoker.pages

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.fetch.circe._
import io.circe.syntax._
import laminar.webcomponents.material.{Button, Dialog, Icon, IconButton, Select, Textfield, List => MList}
import mixzpoker.{App, AppContext, Config, Page}
import mixzpoker.domain.game.GameType
import mixzpoker.domain.lobby.{Lobby, LobbyName}
import mixzpoker.domain.lobby.LobbyRequest._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

object LobbiesPage {

  object requests {
    def getLobbiesRequest()(implicit appContext: Var[AppContext]): EventStream[List[Lobby]] =
      Fetch.get(
          url = s"${Config.rootEndpoint}/lobby",
          headers = Map("Authorization" -> appContext.now().token)
        ).decodeOkay[List[Lobby]]
        .recoverToTry.map(_.fold(
          err => {
            dom.console.log(err.toString)
            List()
          },
          resp => resp.data
        ))

    def createLobbyRequest(name: LobbyName, gametype: GameType)(implicit appContext: Var[AppContext]): EventStream[String] =
      Fetch.post(
          url = s"${Config.rootEndpoint}/lobby/create",
          headers = Map("Authorization" -> appContext.now().token),
          body = CreateLobbyRequest(name, gametype).asJson
        ).text.recoverToTry
        .map(_.fold(_ => "", _ => ""))

  }

  import requests._

  def LobbyItem(lobby: Lobby): MList.ListItem.El = {
    MList.ListItem(
      _ => cls("lobby-list-item"),
      _.`tabindex` := -1,
      _.`graphic` := "avatar",
      _.`twoline` := true,
      _.`hasMeta` := true,
      _.slots.graphic(Icon().amend(span("groups"))),
      _.slots.default(span(lobby.name.toString)),
      _.slots.secondary(span(s"${lobby.gameType}   ${lobby.players.length} / ${lobby.gameSettings.maxPlayers}")),
      _.slots.meta(IconButton(_.`icon` := "groups")),
      _ => onClick --> { _ => App.router.pushState(Page.Lobby(lobby.name.toString)) }
    )
  }

  def apply()(implicit appContext: Var[AppContext]): HtmlElement = {
    val $lobbies: EventStream[ReactiveHtmlElement[HTMLElement]] = getLobbiesRequest().map { lobbies =>
      if (lobbies.isEmpty)
        div("No Lobbies yet!")
      else
        MList(
          _ => idAttr("lobbies-list"),
          _.slots.default(lobbies.map(LobbyItem): _*),
        )
    }

    div(flexDirection.column, h1("Lobbies"), child <-- $lobbies)
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = {
    val isOpen = Var(false)
    val lobbyName = Var("")
    val gameType = Var(GameType.all.head)

    val gameTypeOptions = GameType.all.map { gt =>
      MList.ListItem(_.slots.default(span(gt.toString)))
    }

    div(
      Dialog(
        _.`heading` := "Create Lobby",
        _.`open` <-- isOpen,
        _.slots.primaryAction(Button(
          _.`label` := "Create",
          _.`disabled` <-- lobbyName.signal.map(LobbyName.fromString).map(_.isEmpty),
          _ => inContext { thisNode =>
            thisNode.events(onClick).flatMap { _ =>
              createLobbyRequest(LobbyName.fromString(lobbyName.now()).get, GameType.Poker) //todo fix option
            } --> { _ =>
              isOpen.set(false)
              App.router.pushState(Page.Lobby(lobbyName.now()))
              lobbyName.set("")
            }
          }
        )),
        _.slots.secondaryAction(Button(_.`label` := "Cancel", _ => onClick --> { _ => isOpen.set(false) })),
        _.slots.default(
          p("Create Lobby"),
          div(
            Textfield(
              _.`label` := "Lobby Name",
              _.`value` <-- lobbyName,
              _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> lobbyName}
            ),
            Select(
              _.slots.default(gameTypeOptions: _*),
              _ => inContext { thisNode =>
                onSelect.map(_ => GameType.all(thisNode.ref.`index`.toInt)) --> gameType
              }
            )
          )
        )
      ),
      Button(
        _.`raised` := true,
        _.`label` := "New Lobby",
        _.slots.icon(span("🍉")),
        _ => onClick --> { _ => isOpen.set(true)}
      ),
      Button(
        _.`raised` := true,
        _.`label` := "RAKETA",
        _.slots.icon(span("🚀"))
      )
    )
  }
}
