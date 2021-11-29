package mixzpoker.pages

import com.raquo.laminar.api.L._

object ExceptionPage {

  def apply(exc: Throwable): HtmlElement = div(h1("Not Allowed"), div("Error: "), div(exc.toString))

}
