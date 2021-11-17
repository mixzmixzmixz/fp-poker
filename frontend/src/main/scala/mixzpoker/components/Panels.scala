package mixzpoker.components

import com.raquo.laminar.api.L._

object Panels {
  def SimplePanel(mods: Mod[HtmlElement]*): HtmlElement = {
    div(
      cls("mixz-panel-simple"),
      mods
    )
  }

  def AuthPanel(mods: Mod[HtmlElement]*): HtmlElement = {
    div(
      cls("mixz-panel-auth"),
      div(cls("mixz-container"), mods)
    )
  }
}
