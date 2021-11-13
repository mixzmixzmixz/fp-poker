package mixzpoker.components

import com.raquo.laminar.api.L._

object Buttons {

  def SimpleButton(mods: Mod[HtmlElement]*): HtmlElement = {
    button(
      cls("mixz-btn-simple"),
      mods
    )
  }

}
