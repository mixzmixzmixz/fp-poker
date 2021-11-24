package mixzpoker.pages

import com.raquo.laminar.api.L._
import laminar.webcomponents.material.{Button, Dialog}

object ExceptionPage {

  def apply(exc: Throwable): HtmlElement = div(h1("Not Allowed"), div("Error: "), div(exc.toString))

  def ErrorDialog(err: Var[String]): HtmlElement = {
    Dialog(
      _.`open` <-- err.signal.map(_.nonEmpty),
      _.slots.default(span(child.text <-- err.signal)),
      _.slots.primaryAction(Button(
        _.`label` := "Ok",
        _ => onClick --> {_ => err.set("")}
      ))
    )
  }
}
