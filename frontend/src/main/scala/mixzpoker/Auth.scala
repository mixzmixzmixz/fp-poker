package mixzpoker

import com.raquo.laminar.api.L._

import mixzpoker.components.Navigation._
import mixzpoker.components.Panels._
import mixzpoker.components.Containers._
import mixzpoker.components.Buttons._
import mixzpoker.components.Fields._
import mixzpoker.components.Headers._

object Auth {

  def signInPage(): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")


    SimpleContainer(
      flexDirection.column,
      AuthPanel(
        AuthHeader("Sign In!"),
        TextField("Login: ", loginVar, placeholder("Enter your login here")),
        br(),
        TextField("Password: ", passwordVar),
        br(),
        SimpleContainer(
          flexDirection.row,
          SimpleButton("Sign In!"),
          SimpleButton("Sign Up", navigateTo(Page.SignUpPage)),
        )
      )
    )
  }


  def signUpPage(): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")


    SimpleContainer(
      flexDirection.column,
      AuthPanel(
        AuthHeader("Sign In!"),
        TextField("Login: ", loginVar, placeholder("Enter your login here")),
        br(),
        TextField("Password: ", passwordVar),
        br(),
        SimpleContainer(flexDirection.row, SimpleButton("Sign Up"))
      )
    )
    div(
      "Sign Up Page",
      button(
        "<Nav> Sign in!",
        navigateTo(Page.SignInPage)
      )
    )
  }
}
