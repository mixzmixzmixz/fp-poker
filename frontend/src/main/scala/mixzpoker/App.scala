package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.laminext.syntax.core._
import com.raquo.waypoint._
import upickle.default._
import org.scalajs.dom
import Page._
import laminar.webcomponents.material.{Button, Icon, List, TopAppBar}
import mixzpoker.components.Users.AppUserProfile
import mixzpoker.model.AppState
import mixzpoker.model.AppState._


object App {
  val storedAuthToken: StoredString = storedString("authToken", "")
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/me"
  val appStateVar: Var[AppState] = Var[AppState](AppNotLoaded)

  val routes = scala.List(
    Route.static(SignUpPage,  root / "sign-up" / endOfSegments),
    Route.static(SignInPage,  root / "sign-in" / endOfSegments),
    Route.static(RedirectPage,root /             endOfSegments),
    Route.static(LobbiesPage, root / "lobbies" / endOfSegments),
    Route.static(GamesPage,   root / "games"   / endOfSegments),
  )

  val router = new Router[Page](
    routes = routes,
    getPageTitle = _.toString, // mock page title (displayed in the browser tab next to favicon)
    serializePage = page => write(page)(rwPage), // serialize page data for storage in History API log
    deserializePage = pageStr => read(pageStr)(rwPage), // deserialize the above
    routeFallback = _ => RedirectPage
  )(
    $popStateEvent = L.windowEvents.onPopState, // this is how Waypoint avoids an explicit dependency on Laminar
    owner = L.unsafeWindowOwner // this router will live as long as the window
  )

  def AuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => getUserInfo --> appStateVar),
      child <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => appNotLoaded()
        case AppState.Unauthorized =>
          router.pushState(Page.SignInPage)
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
          router.pushState(Page.LobbiesPage)
          div("Unauthorized <redirect to Main Page>")

      }
    )

  val splitter: SplitRender[Page, HtmlElement] =
    SplitRender[Page, HtmlElement](router.$currentPage)
      .collectStatic(SignUpPage)   { NoAuthFence(Auth.SignUpPage(storedAuthToken))  }
      .collectStatic(SignInPage)   { NoAuthFence(Auth.SignInPage(storedAuthToken))  }
      .collectStatic(RedirectPage) { AuthFence(renderRedirectPage())                }
      .collectSignal[AppPage]      { $appPage => AuthFence(renderAppPage($appPage)) }

  val route: Div = div(child <-- splitter.$view)

  def getUserInfo: EventStream[AppState] = storedAuthToken.signal.flatMap { token =>
    Fetch
      .get(endpointUserInfo, headers = Map("Authorization" -> token)).decodeOkay[AppUserInfo]
      .recoverToTry.map(_.fold(_ => Unauthorized, _.data))
  }

  private def appNotLoaded(): HtmlElement = div("Loading App...")

  private def renderRedirectPage(): HtmlElement = {
    router.pushState(LobbiesPage)
    div("Redirect to Lobbies")
  }

  private def renderAppPage($appPage: Signal[AppPage]): HtmlElement = {
    val appPageSplitter = SplitRender[AppPage, HtmlElement]($appPage)
      .collectStatic(LobbiesPage) { Lobbies() }
      .collectStatic(GamesPage)   { Games() }

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
          _.slots.default()
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
          _ => onClick --> { _ => router.pushState(LobbiesPage)}
        ),
        List.ListItem(
          _.`tabindex` := -1,
          _.`graphic` := "large",
          _.slots.graphic(Icon().amend(textToNode("account_circle"))),
          _.slots.default(span("Games", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(GamesPage)}
        )
      )
    )
  }

  private def Lobbies(): HtmlElement = {
    div("Lobbies")
  }

  private def Games(): HtmlElement = {
    div("Games")
  }

}

