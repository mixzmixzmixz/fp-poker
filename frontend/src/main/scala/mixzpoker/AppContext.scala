package mixzpoker

import mixzpoker.domain.user.UserDto.UserDto
import AppState._
import com.raquo.laminar.api.L._
import io.laminext.syntax.core.{StoredString, storedString}
import mixzpoker.AppContext.noUser

case class AppContext(
  state: AppState,
  user: UserDto,
  token: String,
  storedAuthToken: StoredString,
  error: Var[AppError]
) {
  def unauthorized: AppContext = copy(state = Unauthorized, user = noUser)

  def authorize(user: UserDto, token: String): AppContext = copy(state = Authorized, user = user, token = token)

  def signOut(): AppContext = {
    storedAuthToken.set("")
    copy(state = Unauthorized, user = noUser, token = "")
  }
}

object AppContext {
  def noUser: UserDto = UserDto(name = "--", tokens = 0)

  def init: AppContext =
    AppContext(NotLoaded, noUser, "", storedString("authToken", ""), Var(AppError.NoError))

}
