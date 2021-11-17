package mixzpoker.components

import com.raquo.laminar.api.L._
import org.scalajs.dom
import laminar.webcomponents.material.{TopAppBar, Button}

import mixzpoker.Page
import mixzpoker.App.router
import Users._

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

  def MainNavigation(mods: Modifier[HtmlElement]*): HtmlElement = {
    val name = Var("Mixz")
    val balance = Var(0)

    TopAppBar(
      _.`centerTitle` := true,
      _.`dense` := true,
      _.slots.title(div("MixzPoker")),
      _.slots.navigationIcon(div(
        cls("logo"),
        img(src("frontend/src/main/static/logo.svg"), heightAttr(100))
      )),
      _.slots.actionItems(
        Button(
          _.`raised` := true,
          _.styles.buttonOutlineColor := "#6200ed",
          _.slots.icon(span("🍉")),
          _.`label` := "Lobbies",
          _.slots.default()

        ),
        Button(
          _.`raised` := true,
          _.styles.buttonOutlineColor := "#6200ed",
          _.slots.icon(span("🚀")),
          _.`label` := "Games"
        ),
        AppUserProfile(name.signal, balance.signal)
      )


    )
  }
}
