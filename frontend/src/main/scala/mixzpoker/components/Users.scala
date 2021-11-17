package mixzpoker.components

import com.raquo.laminar.api.L._
import io.laminext.syntax.core._
import laminar.webcomponents.material.{Button, Icon, List, Menu}
import mixzpoker.model.AppState

object Users {

  def AppUserProfile(appStateVar: Var[AppState], storedAuthToken: StoredString): HtmlElement = {
    val menuOpened = Var(false)
    val button = Button(
      _.`raised` := true,
      _.slots.icon(Icon().amend(textToNode("account_circle"))),
      _.`label` <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => "..."
        case AppState.Unauthorized => "Unknown"
        case AppState.AppUserInfo(_, name, _) => name
      },
      _.styles.buttonOutlineColor := "#6200ed",
      _ => onClick --> menuOpened.toggleObserver,
      _ => width := "200px"
    )

    div(
      position.relative,
      button,
      Menu(
        _.`anchor` := button.ref,
        _.`corner` := "BOTTOM_LEFT",
        _.`open` <-- menuOpened.signal,
        _.slots.default(
          List.ListItem(
            _.`twoline` := true,
            _.`noninteractive` := true,
            _.`tabindex` := -1,
            _.`graphic` := "avatar",
            _.slots.graphic(Icon().amend(textToNode("account_circle"))),
            _.slots.default(
              span(
                child.text <-- appStateVar.signal.map {
                  case AppState.AppNotLoaded => "..."
                  case AppState.Unauthorized => "Unknown"
                  case AppState.AppUserInfo(_, name, _) => name
                },
                cls("menu-txt")
              )
            ),
            _.slots.secondary(
              span(
                child.text <-- appStateVar.signal.map {
                  case AppState.AppUserInfo(_, _, b) => s"Balance: $b"
                  case _ => "0"
                },
                cls("menu-txt")
              )
            ),
            _ => width := "200px"
          ),
          List.ListItem(
            _.`tabindex` := -1,
            _.slots.default(
              Button(
                _.`raised` := true,
                _.`label` := "Sign Out!",
                _.`dense` := true,
                _ => onClick --> {_ =>
                  storedAuthToken.set("")
                  appStateVar.set(AppState.Unauthorized)
                }
              )
            )
          )
        ),
        _.onClosed --> { _ => menuOpened.set(false)}
      ),
    )
  }

}
