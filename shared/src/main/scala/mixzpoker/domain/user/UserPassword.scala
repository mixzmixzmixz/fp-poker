package mixzpoker.domain.user


case class UserPassword(value: String) extends AnyVal

object UserPassword {
  //todo validation using cats Validated (here or in the backend)
  def fromString(str: String): UserPassword = UserPassword(str)
}
