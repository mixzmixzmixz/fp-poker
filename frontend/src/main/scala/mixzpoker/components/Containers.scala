package mixzpoker.components

import com.raquo.laminar.api.L._

object Containers {

  def SimpleContainer(mods: Mod[HtmlElement]*): HtmlElement = {
    div(
      cls("mixz-container-simple"),
      flexDirection.column,
      mods
    )
  }

}
