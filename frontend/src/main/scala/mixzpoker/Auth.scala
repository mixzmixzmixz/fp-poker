package mixzpoker

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.circe.syntax._
import io.laminext.core.StoredString
import org.scalajs.dom

import mixzpoker.App.router
import mixzpoker.components.Navigation._
import mixzpoker.components.Panels._
import mixzpoker.components.Containers._
import mixzpoker.components.Buttons._
import mixzpoker.components.Fields._
import mixzpoker.components.Headers._
import mixzpoker.model.UserDto._


object Auth {

  def SignInPage(storedAuthToken: StoredString): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")

    def signInRequest: EventStream[String] = Fetch
      .post(
        url = s"${Config.rootEndpoint}/auth/sign-in",
        body = SignInDto(loginVar.now(), passwordVar.now()).asJson
      )
      .text.recoverToTry
      .map(_.fold(_ => "", resp => {
        if (resp.headers.has("Authorization")) resp.headers.get("Authorization") else ""
      }))

    SimpleContainer(
      flexDirection.column,
      AuthPanel(
        AuthHeader("Sign In"),
        br(), child.text <-- storedAuthToken.signal, br(),
        TextField("Login: ", loginVar, placeholder("Enter your login here")),
        br(),
        TextField("Password: ", passwordVar),
        SimpleContainer(
          flexDirection.row, marginBottom("0px"),
          SimpleButton("Sign In!", inContext { thisNode =>
            val $token = thisNode.events(onClick).flatMap(_ => signInRequest)

            List(
              $token.filterNot(_ == "") --> storedAuthToken.setObserver,
              $token.filterNot(_ == "") --> (_ => router.pushState(Page.MainPage))
            )
          }),
          SimpleButton("Sign Up", navigateTo(Page.SignUpPage)),
        )
      )
    )
  }

  def SignUpPage(storedAuthToken: StoredString): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")

    def signUpRequest: EventStream[String] = Fetch
      .post(
        url = s"${Config.rootEndpoint}/auth/sign-up",
        body = SignUpDto(loginVar.now(), passwordVar.now()).asJson
      )
      .text.recoverToTry
      .map(_.fold(_ => "", resp => {
        if (resp.headers.has("Authorization")) resp.headers.get("Authorization") else ""
      }))


    SimpleContainer(
      flexDirection.column,
      AuthPanel(
        AuthHeader("Sign Up"),
        TextField("Login: ", loginVar, placeholder("Enter your login here")),
        br(),
        TextField("Password: ", passwordVar, placeholder("Do not use your real password!")),
        SimpleContainer(
          flexDirection.row, marginBottom("10px"),
          SimpleButton("Sign Up!",  inContext { thisNode =>
            val $token = thisNode.events(onClick).flatMap(_ => signUpRequest)

            List(
              $token.filterNot(_ == "") --> storedAuthToken.setObserver,
              $token.filterNot(_ == "") --> (_ => router.pushState(Page.MainPage))
            )
          }),
          SimpleButton("Sign In", navigateTo(Page.SignInPage))
        )
      )
    )
  }
}
