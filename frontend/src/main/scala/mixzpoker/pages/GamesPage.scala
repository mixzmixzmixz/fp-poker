package mixzpoker.pages

import com.raquo.laminar.api.L._
import laminar.webcomponents.material.Button
import mixzpoker.AppContext

object GamesPage {

  def apply(): HtmlElement = {
    div("Games")
  }

  def controlButtons()(implicit appContext: Var[AppContext]): HtmlElement = {
    div(
      Button(
        _.`raised` := true,
        _.styles.buttonOutlineColor := "#6200ed",
        _.slots.icon(span("🚀")),
        _.`label` := "New Game"
      ),
      Button(
        _.`raised` := true,
        _.styles.buttonOutlineColor := "#6200ed",
        _.slots.icon(span("🚀")),
        _.`label` := "RAKETA"
      )
    )
  }
}
