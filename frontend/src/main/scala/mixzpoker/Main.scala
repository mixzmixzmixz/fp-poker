package mixzpoker

import org.scalajs.dom
import com.raquo.laminar.api.L._


object Main {
  def main(args: Array[String]): Unit = {

    documentEvents.onDomContentLoaded.foreach { _ =>
      val appContainer = dom.document.getElementById("app-container")
      val appElement = App.rootNode2
      render(appContainer, appElement)
    }(unsafeWindowOwner)
  }
}

