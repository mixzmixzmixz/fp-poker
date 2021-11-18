package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.laminext.syntax.core._
import com.raquo.waypoint._
import upickle.default._
import org.scalajs.dom

import laminar.webcomponents.material.{Button, Icon, List, TopAppBar}
import mixzpoker.components.Users.AppUserProfile
import mixzpoker.model.AppState
import mixzpoker.model.AppState._
import mixzpoker.pages._


object App {
  val storedAuthToken: StoredString = storedString("authToken", "")
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/me"
  val appStateVar: Var[AppState] = Var[AppState](AppNotLoaded)

  val routes = scala.List(
    Route.static(Page.SignUp,  root / "sign-up" / endOfSegments),
    Route.static(Page.SignIn,  root / "sign-in" / endOfSegments),
    Route.static(Page.Redirect,root /             endOfSegments),
    Route.static(Page.Lobbies, root / "lobbies" / endOfSegments),
    Route.static(Page.Games,   root / "games"   / endOfSegments),
  )

  val router = new Router[Page](
    routes = routes,
    getPageTitle = _.toString, // mock page title (displayed in the browser tab next to favicon)
    serializePage = page => write(page)(Page.rwPage), // serialize page data for storage in History API log
    deserializePage = pageStr => read(pageStr)(Page.rwPage), // deserialize the above
    routeFallback = _ => Page.Redirect
  )(
    $popStateEvent = L.windowEvents.onPopState, // this is how Waypoint avoids an explicit dependency on Laminar
    owner = L.unsafeWindowOwner // this router will live as long as the window
  )

  val splitter: SplitRender[Page, HtmlElement] =
    SplitRender[Page, HtmlElement](router.$currentPage)
      .collectStatic(Page.SignUp)   { NoAuthFence(Auth.SignUpPage(storedAuthToken))  }
      .collectStatic(Page.SignIn)   { NoAuthFence(Auth.SignInPage(storedAuthToken))  }
      .collectStatic(Page.Redirect) { AuthFence(renderRedirectPage())                }
      .collectSignal[Page.AppPage]      { $appPage => AuthFence(renderAppPage($appPage)) }

  val route: Div = div(child <-- splitter.$view)

  def getUserInfo: EventStream[AppState] = storedAuthToken.signal.flatMap { token =>
    Fetch
      .get(endpointUserInfo, headers = Map("Authorization" -> token)).decodeOkay[AppUserInfo]
      .recoverToTry.map(_.fold(_ => Unauthorized, _.data))
  }

  def AuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => getUserInfo --> appStateVar),
      child <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => appNotLoaded()
        case AppState.Unauthorized =>
          router.pushState(Page.SignIn)
          div("Unauthorized <redirect to SignIn Page>")
        case appUserInfo: AppUserInfo => page
      }
    )

  def NoAuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => getUserInfo --> appStateVar),
      child <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => appNotLoaded()
        case AppState.Unauthorized => page
        case appUserInfo: AppUserInfo =>
          router.pushState(Page.Lobbies)
          div("Unauthorized <redirect to Main Page>")

      }
    )

  private def appNotLoaded(): HtmlElement = div("Loading App...")

  private def renderRedirectPage(): HtmlElement = {
    router.pushState(Page.Lobbies)
    div("Redirect to Lobbies")
  }

  private def renderAppPage($appPage: Signal[Page.AppPage]): HtmlElement = {
    val appPageSplitter = SplitRender[Page.AppPage, HtmlElement]($appPage)
      .collectStatic(Page.Lobbies) { LobbiesPage() }
      .collectStatic(Page.Games)   { GamesPage() }

    div(
      MainNavigation(),
      div(
        cls := "mixz-container",
        padding := "0",
        flexDirection.row,
        height := "100%",
        width := "100%",
        LeftNavigation(),
        child <-- appPageSplitter.$view
      )
    )
  }

  private def MainNavigation(): HtmlElement = {
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
        ),
        Button(
          _.`raised` := true,
          _.styles.buttonOutlineColor := "#6200ed",
          _.slots.icon(span("🚀")),
          _.`label` := "Games"
        ),
        AppUserProfile(appStateVar, storedAuthToken)
      )


    )
  }

  private def LeftNavigation(): HtmlElement = {
    List(
      _ => cls("mixz-left-panel"),
      _.slots.default(
        List.ListItem(
          _ => marginTop := "0",
          _.`tabindex` := -1,
          _.`graphic` := "large",
          _.slots.graphic(Icon().amend(textToNode("account_circle"))),
          _.slots.default(span("Lobbies", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(Page.Lobbies)}
        ),
        List.ListItem(
          _.`tabindex` := -1,
          _.`graphic` := "large",
          _.slots.graphic(Icon().amend(textToNode("account_circle"))),
          _.slots.default(span("Games", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(Page.Games)}
        )
      )
    )
  }


}

