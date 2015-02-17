package process

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorRef }

object Process {
  case class Perform[S](action: S ⇒ Unit)
  case object GetState
}

trait Process[State] extends Actor {
  def process: ProcessStep[State]
  def state: State
  override def unhandled(msg: Any): Unit = msg match {
    case x if process.doComplete.isDefinedAt(x) =>
      process.doComplete(x)
    case Process.GetState =>
      sender() ! state
  }
}

private class Chain[S](a: ProcessStep[S], b: ProcessStep[S]*) extends ProcessStep[S] {
  override def run()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    a.run() flatMap { _ =>
      Future.sequence(b.map(_.run())).flatMap { _ =>
        promise.trySuccess(())
        future
      }
    }
  }
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")
  def complete: PartialFunction[Any, Unit] = a.doComplete orElse b.foldRight(PartialFunction.empty[Any, Unit]) { case (x, y) => x.doComplete orElse y }
}
