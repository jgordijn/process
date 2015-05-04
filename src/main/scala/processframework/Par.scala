package processframework

import akka.actor.ActorContext

object Par {
  def apply[S](steps: ProcessStep[S]*)(implicit context: ActorContext): ProcessStep[S] = new Chain[S](EmptyStep(), steps: _*)
}
