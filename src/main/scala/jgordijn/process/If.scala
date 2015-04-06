package jgordijn.process

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

class DoNothing[S]()(implicit val context: ActorContext) extends ProcessStep[S] {
  override def execute()(implicit process: ActorRef): Execution = {_ => markDone()}
  override def updateState: UpdateFunction = PartialFunction.empty
  override def receiveCommand: CommandToEvent = PartialFunction.empty
}

object If {
  case class MakeChoice(result: Boolean)
  case class ChoiceResult(result: Boolean) extends Process.Event
  def apply[S](condition: S => Boolean)(process: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) = new If(condition)(process)
}
class If[S](condition: S => Boolean)(process: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) extends Choice[S](condition, process, new DoNothing()) {
  def Else(elseProcess: ProcessStep[S])(implicit context: ActorContext, classTag: ClassTag[S]) = new Choice[S](condition, new DoNothing(), elseProcess)
}
