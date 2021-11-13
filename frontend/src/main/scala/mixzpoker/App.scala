package mixzpoker

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import org.scalajs.dom
import mixzpoker.model.AppState
import mixzpoker.model.AppState._

import scala.util.{Failure, Success}


object App {
  val endpointUserInfo = s"${Config.rootEndpoint}/auth/user"
  val appStateVar = Var[AppState](AppState.notLoaded)

  def initialHook() = {} // todo init app on load

  val rootNode: HtmlElement =
    div(
//      onMountCallback(_ => initialHook()),
      button(
        "Send",
        inContext { thisNode =>
          dom.console.log("AUTH")
          val $response = thisNode.events(onClick).flatMap{ _ =>
            Fetch.get(endpointUserInfo).decodeOkay[AppContext].map{ fr =>
              fr.data.copy(token = fr.headers.get("Authorization"))
            }.recoverToTry.map {
              case Failure(exception) =>
                dom.console.log(exception.getMessage)
                Unauthorized
              case Success(au) => au
            }
          }

          $response --> appStateVar
        }
      ),
      div(
        child <-- appStateVar.signal.map {
          case AppState.AppNotLoaded => appNotLoaded()
          case AppState.Unauthorized => appUnauthorized()
          case context: AppContext => appAuthorized(context)
        }
      ),
      br(),
    )

  val rootNode2 = Router.route

  private def appNotLoaded() =
    div(
      "Loading App..."
    )

  private def appUnauthorized() =
    div(
      "Unauthorized <redirect to Sing In page>"
    )

  private def appAuthorized(context: AppContext) =
    div(
      div("App!"),
      br(),
      div(context.name),
      br(),
      div(context.balance)
    )
}
