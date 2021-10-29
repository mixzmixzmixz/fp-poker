package mixzpoker.fsm

trait FiniteStateMachine[S <: State, E <: Event, Err] {

  // process Event and Return a new FSM with next state
  def processEvent(event: E): Either[Err, FiniteStateMachine[S, E, Err]]
}

