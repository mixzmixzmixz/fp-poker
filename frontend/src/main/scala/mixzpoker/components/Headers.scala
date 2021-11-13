package mixzpoker.components

import com.raquo.laminar.api.L._

object Headers {

  def AuthHeader(title: String, mods: Mod[HtmlElement]*): HtmlElement = {

    div(
      cls("mixz-container"),
      cls("mixz-auth-head"),
      div(title, cls("auth-head-title")),
      mods
    )
  }

}
