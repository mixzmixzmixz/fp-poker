package mixzpoker

import com.raquo.laminar.api.L._
import io.laminext.fetch.circe._
import io.circe.syntax._
import io.laminext.core.StoredString
import laminar.webcomponents.material.{Button, Textfield}
import mixzpoker.App.router
import mixzpoker.components.Navigation._
import mixzpoker.domain.user.UserRequest._


object Auth {

  object requests {

    def signInRequest(body: SignInRequest): EventStream[String] = Fetch
      .post(
        url = s"${Config.rootEndpoint}/auth/sign-in",
        body = body.asJson
      )
      .text.recoverToTry
      .map(_.fold(_ => "", resp => {
        if (resp.headers.has("Authorization")) resp.headers.get("Authorization") else ""
      }))


    def signUpRequest(body: SignUpRequest): EventStream[String] = Fetch
      .post(
        url = s"${Config.rootEndpoint}/auth/sign-up",
        body = body.asJson
      )
      .text.recoverToTry
      .map(_.fold(_ => "", resp => {
        if (resp.headers.has("Authorization")) resp.headers.get("Authorization") else ""
      }))
  }

  import requests._

  def SignInPage(storedAuthToken: StoredString): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")

    div(
      flexDirection.column,
      cls("mixz-panel-auth"),
      div(
        cls("mixz-auth-head"),
        div("Sign In", cls("auth-head-title")),
      ),
      Textfield(
        _ => padding := "20px",
        _.`label` := "Login: ",
        _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> loginVar}
      ),
      br(),
      Textfield(
        _ => padding := "20px",
        _.`label` := "Password: ",
        _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> passwordVar}
      ),
      div(
        cls("mixz-container-simple"),
        flexDirection.row, marginBottom("0px"),
        Button(
          _.`raised` := true,
          _.`label` := "Sign In!",
          _ => inContext { thisNode =>
            val $token = thisNode.events(onClick).flatMap { _ =>
              signInRequest(SignInRequest(loginVar.now().toLowerCase, passwordVar.now().toLowerCase))
            }

            List(
              $token.filterNot(_ == "") --> storedAuthToken.setObserver,
              $token.filterNot(_ == "") --> (_ => router.pushState(Page.Redirect))
            )
          }
        ),
        Button(
          _.`raised` := true,
          _.`label` := "Sign Up",
          _ => navigateTo(Page.SignUp)
        )
      )
    )
  }

  def SignUpPage(storedAuthToken: StoredString): HtmlElement = {
    val loginVar = Var("")
    val passwordVar = Var("")

    div(
      flexDirection.column,
      div(
        cls("mixz-panel-auth"),
        div(
          cls("mixz-auth-head"),
          div("Sign Up", cls("auth-head-title")),
        ),
        Textfield(
          _ => padding := "20px",
          _.`label` := "Login: ",
          _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> loginVar}
        ),
        br(),
        Textfield(
          _ => padding := "20px",
          _.`label` := "Password: ",
          _.`placeholder` := "Do not use your real password!",
          _ => inContext { thisNode => onInput.map(_ => thisNode.ref.`value`) --> passwordVar}
        ),
        div(
          cls("mixz-container-simple"),
          flexDirection.row, marginBottom("10px"),
          Button(
            _.`raised` := true,
            _.`label` := "Sign Up!",
            _ => inContext { thisNode =>
              val $token = thisNode.events(onClick).flatMap { _ =>
                signUpRequest(SignUpRequest(loginVar.now().toLowerCase, passwordVar.now().toLowerCase))
              }

              List(
                $token.filterNot(_ == "") --> storedAuthToken.setObserver,
                $token.filterNot(_ == "") --> (_ => router.pushState(Page.Redirect))
              )
            }
          ),
          Button(
            _.`raised` := true,
            _.`label` := "Sign In",
            _ => navigateTo(Page.SignIn)
          )
        )
      )
    )
  }
}
