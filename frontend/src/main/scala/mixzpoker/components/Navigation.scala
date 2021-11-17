package mixzpoker.components

import com.raquo.laminar.api.L._
import org.scalajs.dom
import mixzpoker.Page
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

}
