package mixzpoker.components

import com.raquo.laminar.api.L._
import io.laminext.syntax.core._
import laminar.webcomponents.material.{Menu, List, Button, Icon}

object Users {

  def AppUserProfile($name: Signal[String], $balance: Signal[Int]): HtmlElement = {
    val menuOpened = Var(false)
    div(
      position.relative,

      Button(
        _.`raised` := true,
        _.slots.icon(Icon().amend(textToNode("account_circle"))),
        _.`label` <-- $name,
        _.styles.buttonOutlineColor := "#6200ed",
        _ => onClick --> menuOpened.toggleObserver,
      ),

      Menu(
        _.`corner` := "BOTTOM_LEFT",
        _.`open` <-- menuOpened.signal,
        _.slots.default(
          List.ListItem(
            _.`twoline` := true,
            _.`noninteractive` := true,
            _.`tabindex` := -1,
            _.`graphic` := "avatar",
            _.slots.graphic(Icon().amend(textToNode("account_circle"))),
            _.slots.default(span(child.text <-- $name, cls("menu-txt"))),
            _.slots.secondary(span(child.text <-- $balance.map(b => s"Balance: $b"), cls("menu-txt"))),
          )
        ),
        _.onClosed --> { _ => menuOpened.set(false)}
      ),
    )
  }

}
