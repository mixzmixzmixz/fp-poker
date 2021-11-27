package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.circe.syntax._
import laminar.webcomponents.material.{Button, Dialog, Icon, Textfield, List => MList}
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.{App, Config, Page}
import mixzpoker.domain.lobby.LobbyDto.{JoinLobbyRequest, LobbyDto}

import scala.util.{Failure, Success, Try}

object LobbyPage {

  object requests {
    def getLobbyRequest(name: String)(implicit token: String): EventStream[Try[LobbyDto]] =
      Fetch.get(
        url = s"${Config.rootEndpoint}/lobby/$name",
        headers = Map("Authorization" -> token)
      ).decodeOkay[LobbyDto].recoverToTry.map(_.map(_.data))

    def joinLobbyRequest(name: String, body: JoinLobbyRequest)(implicit token: String): EventStream[Try[String]] =
      Fetch.post(
        url = s"${Config.rootEndpoint}/lobby/$name/join",
        headers = Map("Authorization" -> token),
        body = body.asJson
      ).text.recoverToTry.map(_.map(_.data))
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

  private def SettingsDialog(isOpen: Var[Boolean], settings: PokerSettings): HtmlElement = {

    val fieldMaxPlayers = Textfield(_.`name` := "Max Players", _.`value` := settings.maxPlayers.toString, _.`outlined` := true)
    val fieldMinPlayers = Textfield(_.`name` := "Min Players", _.`value` := settings.minPlayers.toString, _.`outlined` := true)
    val fieldSmallBlind = Textfield(_.`name` := "Small Blind", _.`value` := settings.smallBlind.toString, _.`outlined` := true)
    val fieldBigBlind   = Textfield(_.`name` := "Big Blind"  , _.`value` := settings.bigBlind.toString, _.`outlined` := true)
    val fieldAnte       = Textfield(_.`name` := "Ante"       , _.`value` := settings.ante.toString, _.`outlined` := true)
    val fieldBuyInMin   = Textfield(_.`name` := "BuyIn Min"  , _.`value` := settings.buyInMin.toString, _.`outlined` := true)
    val fieldBuyInMax   = Textfield(_.`name` := "BuyIn Max"  , _.`value` := settings.buyInMax.toString, _.`outlined` := true)

    Dialog(
      _.`heading` := "Settings",
      _.`open` <-- isOpen,
      _.slots.primaryAction(Button(
        _.`label` := "Create",
        _.`disabled` := false,
        _ => inContext { thisNode =>
          // todo send updateSettings
          thisNode.events(onClick) --> {_ =>
            isOpen.set(false)
          }
        }
      )),
      _.slots.secondaryAction(Button(
        _.`label` := "Cancel",
        _ => onClick --> { _ => isOpen.set(false) }
      )),
      _.slots.default(div(
        display("flex"), flexDirection.column,
        label("Max Players: ", fieldMinPlayers, padding("10px")),
        label("Min Players: ", fieldMaxPlayers, padding("10px")),
        label("Small Blind: ", fieldSmallBlind, padding("10px")),
        label("Big Blind: ", fieldBigBlind, padding("10px")),
        label("Ante: " , fieldAnte, padding("10px")),
        label("BuyIn Min: ", fieldBuyInMin, padding("10px")),
        label("BuyIn Max: ", fieldBuyInMax, padding("10px"))
      ))
    )
  }

  private def renderLobby(lobby: LobbyDto)(implicit token: String): HtmlElement = {
    val isSettingsDialogOpen = Var(false)
    val isJoinLobbyDialogOpen = Var(false)
    val errMsg = Var("")

    def Users() = {
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

    def JoinLobbyDialog(isOpen: Var[Boolean]): HtmlElement = {
      val fieldBuyIn = Var(lobby.gameSettings.buyInMin.toString)

      Dialog(
        _.`heading` := s"Join Lobby ${lobby.name}",
        _.`open` <-- isOpen,
        _.slots.primaryAction(Button(
          _.`label` := "Join",
          _.`disabled` <-- fieldBuyIn.signal.map(_.toIntOption.fold(true)(_ => false)),
          _ => inContext { _.events(onClick).flatMap { _ =>
            joinLobbyRequest(lobby.name, JoinLobbyRequest(fieldBuyIn.now().toInt))
          } --> { _ match {
                case Success(_) =>
                  isOpen.set(false)
                  App.router.replaceState(Page.Lobby(lobby.name))
                case Failure(exc) =>
                  isOpen.set(false)
                  errMsg.set(exc.toString)
              }
            }
          }
        )),
        _.slots.secondaryAction(Button(
          _.`label` := "Cancel",
          _ => onClick --> { _ => isOpen.set(false) }
        )),
        _.slots.default(div(
          display("flex"), flexDirection.column,
          label("Buy In: ", Textfield(
            _.`type` := "number",
            _.`outlined` := true,
            _.`value` <-- fieldBuyIn,
            _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> fieldBuyIn},
          ), padding("10px")),
        ))
      )
    }

    div(
      flexDirection.column,
      width("100%"), height("100%"),
      div(
        cls("lobby-heading"),
        span(lobby.name, cls("lobby-heading-name")),
        SettingsDialog(isSettingsDialogOpen, lobby.gameSettings),
        JoinLobbyDialog(isJoinLobbyDialogOpen),
        ExceptionPage.ErrorDialog(errMsg),
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
          Users()
        )
      )

    )
  }
}
