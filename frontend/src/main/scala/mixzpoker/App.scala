package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.laminext.syntax.core._
import com.raquo.waypoint._
import upickle.default._
import laminar.webcomponents.material.{Icon, List, TopAppBarFixed}
import mixzpoker.components.Users.AppUserProfile
import mixzpoker.model.AppState
import mixzpoker.model.AppState._
import mixzpoker.pages._


object App {
  val storedAuthToken: StoredString = storedString("authToken", "")
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/me"
  val appStateVar: Var[AppState] = Var[AppState](AppNotLoaded)

  object requests {
    def getUserInfo(token: String): EventStream[AppState] = Fetch
      .get(endpointUserInfo, headers = Map("Authorization" -> token)).decodeOkay[AppUserInfo]
      .recoverToTry.map(_.fold(_ => Unauthorized, _.data))
  }

  import requests._

  val routes = scala.List(
    Route.static(Page.SignUp,  root / "sign-up" / endOfSegments),
    Route.static(Page.SignIn,  root / "sign-in" / endOfSegments),
    Route.static(Page.Redirect,root /             endOfSegments),
    Route.static(Page.Lobbies, root / "lobbies" / endOfSegments),
    Route.static(Page.Games,   root / "games"   / endOfSegments),
    Route[Page.Lobby, String](
      encode = lobbyPage => lobbyPage.name,
      decode = arg => Page.Lobby(name = arg),
      pattern = root / "lobby" / segment[String] / endOfSegments
    )
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
      .collectStatic(Page.Redirect) { AuthFence(renderRedirectPage)                  }
      .collectSignal[Page.AppPage]  { $appPage => AuthFence(renderAppPage($appPage)) }

  val route: Div = div(child <-- splitter.$view)

  def AuthFence(page: String => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => storedAuthToken.signal.flatMap(token => getUserInfo(token)) --> appStateVar),
      child <-- storedAuthToken.signal.map { token =>
        div(
          child <-- appStateVar.signal.map {
            case AppState.AppNotLoaded => appNotLoaded()
            case AppState.Unauthorized =>
              router.pushState(Page.SignIn)
              div("Unauthorized <redirect to SignIn Page>")
            case appUserInfo: AppUserInfo => page(token)
          }
        )
      }

    )

  def NoAuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => storedAuthToken.signal.flatMap(token => getUserInfo(token)) --> appStateVar),
      child <-- storedAuthToken.signal.map { token =>
        div(
          child <-- appStateVar.signal.map {
            case AppState.AppNotLoaded => appNotLoaded()
            case AppState.Unauthorized => page
            case appUserInfo: AppUserInfo =>
              router.pushState(Page.Lobbies)
              div("Unauthorized <redirect to Main Page>")
          }
        )
      }

    )

  private def appNotLoaded(): HtmlElement = div("Loading App...")

  private def renderRedirectPage(token: String): HtmlElement = {
    router.pushState(Page.Lobbies)
    div("Redirect to Lobbies")
  }

  private def renderAppPage($appPage: Signal[Page.AppPage])(token: String): HtmlElement = {
    implicit val authToken: String = token
    val appPageSplitter = SplitRender[Page.AppPage, HtmlElement]($appPage)
      .collectStatic(Page.Lobbies) { LobbiesPage() }
      .collectStatic(Page.Games)   { GamesPage() }
      .collectSignal[Page.Lobby]   { $lobbyPage => LobbyPage($lobbyPage)}

    val buttonsSplitter = SplitRender[Page.AppPage, HtmlElement]($appPage)
      .collectStatic(Page.Lobbies) { LobbiesPage.controlButtons() }
      .collectStatic(Page.Games)   { GamesPage.controlButtons() }
      .collectSignal[Page.Lobby]   { _ => LobbyPage.controlButtons()}


    div(
      TopAppBarFixed(
        _.`centerTitle` := true,
        _.`dense` := true,
        _.slots.title(div("MixzPoker")),
        _.slots.navigationIcon(div(
          cls("logo"),
          img(src("frontend/src/main/static/logo.svg"), heightAttr(100))
        )),
        _.slots.actionItems(
          div(
            child <-- buttonsSplitter.$view
          ),
          AppUserProfile(appStateVar, storedAuthToken)
        ),
        _.slots.default(
          div(
            cls := "mixz-container",
            background := "#bbf1ee",
            padding := "0",
            flexDirection.row,
            height := "100%",
            width := "100%",
            LeftNavigation(),
            child <-- appPageSplitter.$view
          )
        )
      )
    )
  }

  private def LeftNavigation(): HtmlElement = {
    div(List(
      _ => idAttr("left-nav-list"),
      _ => cls("mixz-left-panel"),
      _.slots.default(
        List.ListItem(
          _ => marginTop := "0",
          _.`tabindex` := -1,
          _.`graphic` := "large",
          _.slots.graphic(Icon().amend(span("chevron_right"))),
          _.slots.default(span("Lobbies", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(Page.Lobbies)}
        ),
        List.ListItem(
          _.`tabindex` := -1,
          _.`graphic` := "large",
          _.slots.graphic(Icon().amend(span("chevron_right"))),
          _.slots.default(span("Games", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(Page.Games)}
        )
      )
    ))
  }


}

