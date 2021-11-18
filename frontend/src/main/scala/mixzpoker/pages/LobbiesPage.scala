package mixzpoker.pages

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._

import mixzpoker.Config

object LobbiesPage {

  def signInRequest: EventStream[String] = Fetch
    .get(url = s"${Config.rootEndpoint}/lobby")
    .text.recoverToTry
    .map(_.fold(_ => "", resp => ""))

  def apply(): HtmlElement = {
    div("Lobbies")
  }
}
