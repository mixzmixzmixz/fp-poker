package mixzpoker.components

import com.raquo.laminar.api.L._

object Fields {

  def TextField(labelName: String, textVar: Var[String], inputMods: Mod[Input]*): HtmlElement = {
    div(
      label(labelName, cls("mixz-field-label")),
      input(
        cls("mixz-field-text"),
        onMountFocus,
        inputMods,
        inContext { thisNode => onInput.map(_ => thisNode.ref.value) --> textVar }
      ),

    )
  }

}
