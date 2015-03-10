package jgordijn.process

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef }

object Process {
  case object GetState
  trait Event
}

trait Process[State] extends Actor {
  def process: ProcessStep[State]
  var state: State
  override def unhandled(msg: Any): Unit = msg match {
    case x if process.handleReceiveCommand.isDefinedAt(x) =>
      val event = process.handleReceiveCommand(x)
      self ! event
    case event: Process.Event =>
      state = process.handleUpdateState(event)(state)
    case Process.GetState =>
      sender() ! state
  }
}

private class Chain[S](a: ProcessStep[S], b: ProcessStep[S]*)(implicit val context: ActorContext) extends ProcessStep[S] {
  override def run()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    a.run() flatMap { _ =>
      Future.sequence(b.map(_.run())).flatMap { _ =>
        promise.trySuccess(())
        promise.future
      }
    }
  }
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")

  def receiveCommand: PartialFunction[Any, Process.Event] = a.handleReceiveCommand orElse b.foldRight(PartialFunction.empty[Any, Process.Event]) { case (x, y) => x.handleReceiveCommand orElse y }
  def updateState: PartialFunction[Process.Event, S => S] = a.handleUpdateState orElse b.foldRight(PartialFunction.empty[Process.Event, S => S]) { case (x, y) => x.handleUpdateState orElse y }
}
