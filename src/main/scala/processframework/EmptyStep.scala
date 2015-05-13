package processframework

import akka.actor.{ ActorContext, ActorRef }

object EmptyStep {
  def apply[S]()(implicit context: ActorContext): EmptyStep[S] = new EmptyStep()
}
class EmptyStep[S]()(implicit val context: ActorContext) extends ProcessStep[S] {
  markDone()

  override def execute()(implicit process: ActorRef): Execution = { _ => }
  override def updateState: UpdateFunction = PartialFunction.empty
  override def receiveCommand: CommandToEvent = PartialFunction.empty
}
