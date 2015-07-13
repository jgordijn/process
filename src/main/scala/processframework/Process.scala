package processframework

import akka.actor.Actor

object Process {
  case object GetState
  trait Event
  trait AbortCommand
  trait AbortEvent extends Event
}

trait Process[State] extends Actor {
  def process: ProcessStep[State]
  var state: State
  override def unhandled(msg: Any): Unit = msg match {
    case x if process.handleReceiveCommand.isDefinedAt(x) ⇒
      val event = process.handleReceiveCommand(x)
      self ! event
    case event: Process.Event ⇒
      state = process.handleUpdateState(event)(state)
    case Process.GetState ⇒
      sender() ! state
  }
}
