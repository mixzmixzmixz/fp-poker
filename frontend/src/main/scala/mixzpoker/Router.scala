package mixzpoker

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.waypoint._
import upickle.default._
import org.scalajs.dom
import Page._

//
//case class UserPage(userId: Int) extends Page
//val userRoute = Route(
//  encode = userPage => userPage.userId,
//  decode = arg => UserPage(userId = arg),
//  pattern = root / "user" / segment[Int] / endOfSegments
//)

object Router {
  val routes = List(
    Route.static(SignUpPage, root / "sign-up" / endOfSegments),
    Route.static(SignInPage, root / "sign-in" / endOfSegments),
    Route.static(RootPage, root / endOfSegments),
  )

  val router = new Router[Page](
    routes = routes,
    getPageTitle = _.toString, // mock page title (displayed in the browser tab next to favicon)
    serializePage = page => write(page)(rw), // serialize page data for storage in History API log
    deserializePage = pageStr => read(pageStr)(rw), // deserialize the above
    routeFallback = _ => RootPage
  )(
    $popStateEvent = L.windowEvents.onPopState, // this is how Waypoint avoids an explicit dependency on Laminar
    owner = L.unsafeWindowOwner // this router will live as long as the window
  )

  val splitter = SplitRender[Page, HtmlElement](router.$currentPage)
//    .collectSignal[UserPage] { $userPage => renderUserPage($userPage) }
    .collectStatic(SignUpPage) { Auth.signUpPage() }
    .collectStatic(SignInPage) { Auth.signInPage() }
    .collectStatic(RootPage) { div("Root page") }



  val route: Div = div(
    child <-- splitter.$view
  )
}
