package mixzpoker.components

import com.raquo.laminar.api.L._
import laminar.webcomponents.material.Button
import org.scalajs.dom
import mixzpoker.{AppContext, Page}
import mixzpoker.App.router

object Navigation {
  //from waypoint doc
  def navigateTo(page: Page): Binder[HtmlElement] = Binder { el =>
    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]
    if (isLinkElement) {
      el.amend(href(router.absoluteUrlForPage(page)))
    }

    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault
      --> (_ => router.pushState(page))
      ).bind(el)
  }

  def DefaultTopButtons()(implicit appContext: Var[AppContext]): HtmlElement = {
//    div(
//      Button(
//        _.`raised` := true,
//        _.styles.buttonOutlineColor := "#6200ed",
//        _.slots.icon(span("🚀")),
//        _.`label` := "DefButton1"
//      ),
//      Button(
//        _.`raised` := true,
//        _.styles.buttonOutlineColor := "#6200ed",
//        _.slots.icon(span("🚀")),
//        _.`label` := "RAKETA"
//      )
//    )

    div()
  }

}
