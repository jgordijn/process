package process

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorRef }

object Process {
  case object GetState
}

trait Process[State] extends Actor {
  def process: ProcessStep[State]
  var state: State
  override def unhandled(msg: Any): Unit = msg match {
    case x if process.getUpdateStateAction.isDefinedAt(x) =>
      state = process.getUpdateStateAction(x)(state)
    case Process.GetState =>
      sender() ! state
  }
}

private class Chain[S](a: ProcessStep[S], b: ProcessStep[S]*) extends ProcessStep[S] {
  override def run()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    a.run() flatMap { _ =>
      Future.sequence(b.map(_.run())).flatMap { _ =>
        promise.trySuccess(())
        promise.future
      }
    }
  }
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")
  def complete: PartialFunction[Any, S => S] = a.getUpdateStateAction orElse b.foldRight(PartialFunction.empty[Any, S => S]) { case (x, y) => x.getUpdateStateAction orElse y }
}
