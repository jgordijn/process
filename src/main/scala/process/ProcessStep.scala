package process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

trait ProcessStep[S] {
  val promise: Promise[Unit] = Promise[Unit]()
  def isCompleted = promise.isCompleted
  def execute()(implicit process: ActorRef): S => Unit
  def complete: PartialFunction[Any, S => S]
  def getUpdateStateAction: PartialFunction[Any, S => S] = if(promise.future.isCompleted) PartialFunction.empty[Any, S => S] else complete
  def markDone(): Unit = promise.trySuccess(())

  def ~>(next: ProcessStep[S]*): ProcessStep[S] = new Chain(this, next: _*)
  def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!promise.isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute())
    promise.future
  }
}
