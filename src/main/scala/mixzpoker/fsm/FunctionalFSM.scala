package mixzpoker.fsm

case class FunctionalFSM[S <: State, E <: Event, Err](
  state: S, transition: (S, E) => Either[Err, S]
) extends FiniteStateMachine[S, E, Err] {

  override def processEvent(event: E): Either[Err, FiniteStateMachine[S, E, Err]] = {
    transition(state, event) match {
      case Left(value) => Left(value)
      case Right(nextState) => Right(copy(state = nextState))
    }

  }

}

object FunctionalFSM {
  def fromTransition[S <: State, E <: Event, Err](
    initialState: S, transition: (S, E) => Either[Err, S]
  ): FunctionalFSM[S, E, Err] =
    FunctionalFSM(initialState, transition)
}
