package jgordijn.process

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

object EmptyStep {
  def apply[S]()(implicit context: ActorContext): ProcessStep[S] = new EmptyStep()
}
class EmptyStep[S]()(implicit val context: ActorContext) extends ProcessStep[S] {
  override def execute()(implicit process: ActorRef): Execution = {_ => markDone()}
  override def updateState: UpdateFunction = PartialFunction.empty
  override def receiveCommand: CommandToEvent = PartialFunction.empty
}
