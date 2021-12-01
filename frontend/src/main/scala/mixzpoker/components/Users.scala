package mixzpoker.components

import com.raquo.laminar.api.L._
import io.laminext.syntax.core._
import laminar.webcomponents.material.{Button, Icon, List, Menu}
import mixzpoker.AppState
import mixzpoker.domain.user.UserDto.UserDto

object Users {

  def AppUserProfile(appState: Var[AppState], $appUser: Signal[UserDto], storedAuthToken: StoredString): HtmlElement = {
    val menuOpened = Var(false)
    val button = Button(
      _.`raised` := true,
      _.slots.icon(Icon().amend(textToNode("account_circle"))),
      _.`label` <-- $appUser.map(_.name),
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
                child.text <-- $appUser.map(_.name),
                cls("menu-txt")
              )
            ),
            _.slots.secondary(
              span(
                child.text <-- $appUser.map(user => s"Balance: ${user.tokens}"),
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
                  appState.set(AppState.Unauthorized)
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
