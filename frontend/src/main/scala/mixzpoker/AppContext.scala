package mixzpoker

import com.raquo.laminar.api.L._
import io.laminext.syntax.core.{StoredString, storedString}
import mixzpoker.AppContext.noUser
import mixzpoker.domain.user.User
import AppState._


final case class AppContext(
  state: AppState,
  user: User,
  token: String,
  storedAuthToken: StoredString,
  error: Var[AppError]
) {
  def unauthorized: AppContext = copy(state = Unauthorized, user = noUser)

  def authorize(user: User, token: String): AppContext = copy(state = Authorized, user = user, token = token)

  def signOut(): AppContext = {
    storedAuthToken.set("")
    copy(state = Unauthorized, user = noUser, token = "")
  }
}

object AppContext {
  def noUser: User = User.empty

  def init: AppContext =
    AppContext(NotLoaded, noUser, "", storedString("authToken", ""), Var(AppError.NoError))

}
