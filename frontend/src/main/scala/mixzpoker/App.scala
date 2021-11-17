package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.laminext.syntax.core._
import com.raquo.waypoint._
import upickle.default._
import org.scalajs.dom

import Page._
import mixzpoker.components.Navigation.MainNavigation
import mixzpoker.model.AppState
import mixzpoker.model.AppState._


object App {
  val storedAuthToken: StoredString = storedString("authToken", "")
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/me"
  val appStateVar: Var[AppState] = Var[AppState](AppNotLoaded)

  val routes = List(
    Route.static(SignUpPage, root / "sign-up" / endOfSegments),
    Route.static(SignInPage, root / "sign-in" / endOfSegments),
    Route.static(MainPage, root / endOfSegments),
  )

  val router = new Router[Page](
    routes = routes,
    getPageTitle = _.toString, // mock page title (displayed in the browser tab next to favicon)
    serializePage = page => write(page)(rw), // serialize page data for storage in History API log
    deserializePage = pageStr => read(pageStr)(rw), // deserialize the above
    routeFallback = _ => MainPage
  )(
    $popStateEvent = L.windowEvents.onPopState, // this is how Waypoint avoids an explicit dependency on Laminar
    owner = L.unsafeWindowOwner // this router will live as long as the window
  )

  def AuthFence(page: AppUserInfo => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => getUserInfo --> appStateVar),
      child <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => appNotLoaded()
        case AppState.Unauthorized =>
          router.pushState(Page.SignInPage)
          div("Unauthorized <redirect to SignIn Page>")
        case appUserInfo: AppUserInfo => page(appUserInfo)
      }
    )

  def NoAuthFence(page: => HtmlElement): HtmlElement =
    div(
      onMountBind(_ => getUserInfo --> appStateVar),
      child <-- appStateVar.signal.map {
        case AppState.AppNotLoaded => appNotLoaded()
        case AppState.Unauthorized => page
        case appUserInfo: AppUserInfo =>
          router.pushState(Page.MainPage)
          div("Unauthorized <redirect to Main Page>")

      }
    )

  val splitter: SplitRender[Page, HtmlElement] =
    SplitRender[Page, HtmlElement](router.$currentPage)
      .collectStatic(SignUpPage) { NoAuthFence(Auth.SignUpPage(storedAuthToken)) }
      .collectStatic(SignInPage) { NoAuthFence(Auth.SignInPage(storedAuthToken)) }
      .collectStatic(MainPage)   { AuthFence(appMainPage) }

  val route: Div = div(child <-- splitter.$view)


  def getUserInfo: EventStream[AppState] = storedAuthToken.signal.flatMap { token =>
    Fetch
      .get(endpointUserInfo, headers = Map("Authorization" -> token)).decodeOkay[AppUserInfo]
      .recoverToTry.map(_.fold(_ => Unauthorized, _.data))
  }

  private def appNotLoaded(): HtmlElement = div("Loading App...")

  private def appMainPage(appUserInfo: AppUserInfo): HtmlElement =
    div(
      MainNavigation(),
      div("Hello there")
    )
}
