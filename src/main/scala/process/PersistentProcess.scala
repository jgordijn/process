package process

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect._

import akka.actor.{ Actor, ActorRef }
import akka.persistence.{ PersistentActor, RecoveryCompleted }

abstract class PersistentProcess[State : ClassTag] extends PersistentActor {
  def process: ProcessStep[State]
  var state: State

  final def receiveRecover = {
    case event: Process.Event =>
      state = process.handleUpdateState(event)(state)
    case RecoveryCompleted =>
      import context.dispatcher
      process.run()
  }

  def receiveCommand = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case x if process.handleReceiveCommand.isDefinedAt(x) =>
      val event = process.handleReceiveCommand(x)
      self ! event
    case event: Process.Event =>
      persist(event) { event =>
        state = process.handleUpdateState(event)(state)
      }
    case Process.GetState =>
      sender() ! state
    case m =>
      super.unhandled(m)
  }
}
