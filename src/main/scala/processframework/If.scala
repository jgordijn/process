package processframework

import scala.reflect.ClassTag

import akka.actor.ActorContext

object If {
  def apply[S](condition: S ⇒ Boolean)(process: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) = new If(condition)(process)
}
class If[S](condition: S ⇒ Boolean)(process: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) extends Choice[S](condition, process, EmptyStep()) {
  def Else(elseProcess: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) = new Choice[S](condition, process, elseProcess)
}
