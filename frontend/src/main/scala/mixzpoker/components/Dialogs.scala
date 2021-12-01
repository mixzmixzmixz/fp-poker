package mixzpoker.components

import com.raquo.laminar.api.L._
import io.laminext.websocket.WebSocket
import laminar.webcomponents.material.{Button, Dialog, Textfield}

import mixzpoker.AppError
import mixzpoker.AppError.GeneralError
import mixzpoker.domain.game.GameSettings
import mixzpoker.domain.game.poker.PokerSettings
import mixzpoker.domain.lobby.LobbyDto.LobbyDto
import mixzpoker.domain.lobby.{LobbyInputMessage, LobbyOutputMessage}


object Dialogs {
  def ErrorDialog(err: Var[AppError]): HtmlElement = {
    Dialog(
      _.`open` <-- err.signal.map {
        case AppError.NoError => false
        case _                => true
      },
      _.slots.default(span(child.text <-- err.signal.map(_.toString))),
      _.slots.primaryAction(Button(
        _.`label` := "Ok",
        _ => onClick --> {_ => err.set(AppError.NoError)}
      ))
    )
  }

  def JoinLobbyDialog(
    isOpen: Var[Boolean], lobby: LobbyDto, ws: WebSocket[LobbyOutputMessage, LobbyInputMessage]
  ): HtmlElement = {
    val fieldBuyIn = Var(lobby.gameSettings.buyInMin.toString)

    Dialog(
      _.`heading` := s"Join Lobby ${lobby.name}",
      _.`open` <-- isOpen,
      _.slots.primaryAction(Button(
        _.`label` := "Join",
        _.`disabled` <-- fieldBuyIn.signal.map(_.toIntOption.fold(true)(_ => false)),
        _ => onClick --> { _ =>
          ws.sendOne(LobbyInputMessage.Join(fieldBuyIn.now().toInt))
          isOpen.set(false)
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

  def SettingsDialog(isOpen: Var[Boolean], settings: GameSettings): HtmlElement = settings match {
    case ps: PokerSettings => PokerSettingsDialog(isOpen, ps)
    case _ => ErrorDialog(Var(GeneralError("Wrong settings type"))) //todo appropriate err msg
  }

  def PokerSettingsDialog(isOpen: Var[Boolean], settings: PokerSettings): HtmlElement = {

    val fieldMaxPlayers = Textfield(_.`name` := "Max Players", _.`value` := settings.maxPlayers.toString, _.`outlined` := true)
    val fieldMinPlayers = Textfield(_.`name` := "Min Players", _.`value` := settings.minPlayers.toString, _.`outlined` := true)
    val fieldSmallBlind = Textfield(_.`name` := "Small Blind", _.`value` := settings.smallBlind.toString, _.`outlined` := true)
    val fieldBigBlind   = Textfield(_.`name` := "Big Blind"  , _.`value` := settings.bigBlind.toString,   _.`outlined` := true)
    val fieldAnte       = Textfield(_.`name` := "Ante"       , _.`value` := settings.ante.toString,       _.`outlined` := true)
    val fieldBuyInMin   = Textfield(_.`name` := "BuyIn Min"  , _.`value` := settings.buyInMin.toString,   _.`outlined` := true)
    val fieldBuyInMax   = Textfield(_.`name` := "BuyIn Max"  , _.`value` := settings.buyInMax.toString,   _.`outlined` := true)

    Dialog(
      _.`heading` := "Settings",
      _.`open` <-- isOpen,
      _.slots.primaryAction(Button(
        _.`label` := "Create",
        _.`disabled` := false,
        _ => onClick --> { _ => isOpen.set(false) }
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
        label("Big Blind: ",   fieldBigBlind, padding("10px")),
        label("Ante: " ,       fieldAnte, padding("10px")),
        label("BuyIn Min: ",   fieldBuyInMin, padding("10px")),
        label("BuyIn Max: ",   fieldBuyInMax, padding("10px"))
      ))
    )
  }

}
