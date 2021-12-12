package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import com.raquo.waypoint._
import upickle.default._
import laminar.webcomponents.material.{Icon, List, TopAppBarFixed}
import mixzpoker.components.Users.AppUserProfile
import mixzpoker.AppState._
import mixzpoker.components.Dialogs.ErrorDialog
import mixzpoker.domain.user.UserDto.UserDto
import mixzpoker.pages._


object App {
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/me"
  implicit val appContext: Var[AppContext] = Var(AppContext.init)

  object requests {
    def getUserInfo(token: String): EventStream[AppContext] = Fetch
      .get(endpointUserInfo, headers = Map("Authorization" -> token)).decodeOkay[UserDto]
      .recoverToTry
      .map(_.fold(_ => appContext.now().unauthorized, resp => appContext.now().authorize(resp.data, token)))
  }

  import requests._

  val routes = scala.List(
    Route.static(Page.SignUp,  root / "sign-up" / endOfSegments),
    Route.static(Page.SignIn,  root / "sign-in" / endOfSegments),
    Route.static(Page.Redirect,root /             endOfSegments),
    Route.static(Page.Lobbies, root / "lobbies" / endOfSegments),
    Route.static(Page.PokerGames,   root / "games"   / endOfSegments),
    Route[Page.Lobby, String](
      encode = lobbyPage => lobbyPage.name,
      decode = arg => Page.Lobby(name = arg),
      pattern = root / "lobby" / segment[String] / endOfSegments
    ),
    Route[Page.PokerGame, String](
      encode = gamePage => gamePage.id,
      decode = arg => Page.PokerGame(id = arg),
      pattern = root / "game" / segment[String] / endOfSegments
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
      .collectStatic(Page.SignUp)   { NoAuthFence(Auth.SignUpPage(appContext.now().storedAuthToken))  }
      .collectStatic(Page.SignIn)   { NoAuthFence(Auth.SignInPage(appContext.now().storedAuthToken))  }
      .collectStatic(Page.Redirect) { AuthFence(renderRedirectPage())                  }
      .collectSignal[Page.AppPage]  { $appPage => AuthFence(renderAppPage($appPage)) }

  val route: Div = div(height := "100%", width := "100%", child <-- splitter.$view)

  def AuthFence(page: => HtmlElement): HtmlElement =
    div(
      height := "100%", width := "100%",
      onMountBind(_ => appContext.now().storedAuthToken.signal.flatMap(token => getUserInfo(token)) --> appContext),
      child <-- appContext.signal.map(_.state).map {
        case NotLoaded    => appNotLoaded()
        case Unauthorized =>
          router.pushState(Page.SignIn)
          div("Unauthorized <redirect to SignIn Page>")
        case Authorized   => page
      }
    )

  def NoAuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => appContext.now().storedAuthToken.signal.flatMap(token => getUserInfo(token)) --> appContext),
      child <-- appContext.signal.map(_.state).map {
        case NotLoaded    => appNotLoaded()
        case Unauthorized => page
        case Authorized   =>
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
      .collectStatic(Page.PokerGames)   { PokerGamesPage() }
      .collectSignal[Page.Lobby]   { $lobbyPage => LobbyPage($lobbyPage) }
      .collectSignal[Page.PokerGame]    { $gamePage  => PokerGamePage($gamePage) }

    val buttonsSplitter = SplitRender[Page.AppPage, HtmlElement]($appPage)
      .collectStatic(Page.Lobbies) { LobbiesPage.controlButtons() }
      .collectStatic(Page.PokerGames)   { PokerGamesPage.controlButtons() }
      .collectSignal[Page.Lobby]   { _ => LobbyPage.controlButtons() }
      .collectSignal[Page.PokerGame]    { _ => PokerGamePage.controlButtons() }

    TopAppBarFixed(
      _.`centerTitle` := true,
      _.`dense` := true,
      _.slots.title(div("MixzPoker")),
      _.slots.navigationIcon(div(cls := "logo", img(src := "frontend/src/main/static/logo.svg", heightAttr := 80))),
      _.slots.actionItems(
        div(child <-- buttonsSplitter.$view),
        AppUserProfile()
      ),
      _.slots.default(
        div(
          cls := "base-app-container",
          LeftNavigation(),
          ErrorDialog(appContext.now().error),
          child <-- appPageSplitter.$view
        )
      )
    )
  }

  private def LeftNavigation(): HtmlElement = {
    List(
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
          _.slots.default(span("PokerGames", cls("mixz-panel-text"))),
          _ => width := "200px",
          _ => onClick --> { _ => router.pushState(Page.PokerGames)}
        )
      )
    )
  }


}

