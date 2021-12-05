package mixzpoker.domain.user

case class UserName(value: String) extends AnyVal {
  override def toString: String = value
}