package process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout


trait ProcessStep[S] {
  val promise: Promise[Unit] = Promise[Unit]()
  val future = promise.future
  def execute()(implicit process: ActorRef): S => Unit
  def complete: PartialFunction[Any, Unit]
  def doComplete: PartialFunction[Any, Unit] = if(future.isCompleted) PartialFunction.empty[Any, Unit] else complete

  def ~>(next: ProcessStep[S]*): ProcessStep[S] = new Chain(this, next: _*)
  def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!promise.isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute())
    future
  }
}
