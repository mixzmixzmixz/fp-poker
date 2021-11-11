package mixzpoker

import org.scalajs.dom
import com.raquo.laminar.api.L._


// Render app
object Main {

  val containerNode = dom.document.getElementById("app-container")

  def main(args: Array[String]): Unit = {
    render(containerNode, App.node)
  }
}





//
//object Main {
//
//
//  def main(args: Array[String]): Unit = {
//    render(containerNode, rootElement)
//  }
//
//  val nameVar = Var(initial = "world")
//
//  val rootElement = div(
//    label("Your name: "),
//    input(
//      onMountFocus,
//      placeholder := "Enter your name here",
//      inContext { thisNode => onInput.map(_ => thisNode.ref.value) --> nameVar }
//    ),
//    span(
//      "Hello, ",
//      child.text <-- nameVar.signal.map(_.toUpperCase)
//    )
//  )
//
//}
