package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import laminar.webcomponents.material.{Button, Dialog, Formfield, Icon, Textfield, List => MList}
import mixzpoker.model.GameSettings.PokerSettings
import mixzpoker.{Config, Page}
import mixzpoker.model.LobbyDto.Lobby

import scala.util.{Failure, Success, Try}

object LobbyPage {

  object requests {
    def getLobbyRequest(name: String)(implicit token: String): EventStream[Try[Lobby]] =
      Fetch.get(
        url = s"${Config.rootEndpoint}/lobby/$name",
        headers = Map("Authorization" -> token)
      ).decodeOkay[Lobby].recoverToTry.map(_.map(_.data))
  }

  import requests._

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

  private def Settings(isSettingsDialogOpen: Var[Boolean], settings: PokerSettings): HtmlElement = {

    val fieldMaxPlayers = Textfield(_.`name` := "Max Players", _.`value` := settings.maxPlayers.toString, _.`outlined` := true)
    val fieldMinPlayers = Textfield(_.`name` := "Min Players", _.`value` := settings.minPlayers.toString, _.`outlined` := true)
    val fieldSmallBlind = Textfield(_.`name` := "Small Blind", _.`value` := settings.smallBlind.toString, _.`outlined` := true)
    val fieldBigBlind   = Textfield(_.`name` := "Big Blind"  , _.`value` := settings.bigBlind.toString, _.`outlined` := true)
    val fieldAnte       = Textfield(_.`name` := "Ante"       , _.`value` := settings.ante.toString, _.`outlined` := true)
    val fieldBuyInMin   = Textfield(_.`name` := "BuyIn Min"  , _.`value` := settings.buyInMin.toString, _.`outlined` := true)
    val fieldBuyInMax   = Textfield(_.`name` := "BuyIn Max"  , _.`value` := settings.buyInMax.toString, _.`outlined` := true)

    Dialog(
      _.`heading` := "Settings",
      _.`open` <-- isSettingsDialogOpen,
      _.slots.primaryAction(Button(
        _.`label` := "Create",
        _.`disabled` := false,
        _ => inContext { thisNode =>
          // todo send updateSettings
          thisNode.events(onClick) --> {_ =>
            isSettingsDialogOpen.set(false)
          }
        }
      )),
      _.slots.secondaryAction(Button(
        _.`label` := "Cancel",
        _ => onClick --> { _ => isSettingsDialogOpen.set(false) }
      )),
      _.slots.default(div(
        display("flex"), flexDirection.column,
        label("Max Players: ", fieldMinPlayers),
        label("Min Players: ", fieldMaxPlayers),
        label("Small Blind: ", fieldSmallBlind),
        label("Big Blind: ", fieldBigBlind),
        label("Ante: " , fieldAnte),
        label("BuyIn Min: ", fieldBuyInMin),
        label("BuyIn Max: ", fieldBuyInMax)
      ))
    )
  }

  private def renderLobby(lobby: Lobby): HtmlElement = {
    val isSettingsDialogOpen = Var(false)

    def Users() = {
      if (lobby.users.isEmpty)
        div("No Users!")
      else
        MList(
          _.slots.default(
            lobby.users.map { case (user, buyIn) =>
              MList.ListItem(
                _.`graphic` := "avatar",
                _.`twoline` := true,
                _.`hasMeta` := true,
                _.slots.graphic(Icon().amend(span("person"))),
                _.slots.default(span(user.name)),
                _.slots.secondary(span(buyIn.toString)),
              )
            }: _*
          )
        )
    }

    div(
      flexDirection.column,
      width("100%"), height("100%"),
      div(
        cls("lobby-heading"),
        span(lobby.name, cls("lobby-heading-name")),
        Settings(isSettingsDialogOpen, lobby.gameSettings),
        div(
          float("right"), marginRight("0"), marginLeft("auto"),
          Button(_.`raised` := true, _.`label` := "join", _ => cls("lobby-heading-btn")), //todo or leave
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
          Users()
        )
      )

    )
  }
}
