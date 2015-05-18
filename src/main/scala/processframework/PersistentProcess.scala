package processframework

import akka.actor.Actor._
import akka.actor.{ ActorLogging, Actor, ActorContext }
import akka.persistence.{ PersistentActor, RecoveryCompleted }
import processframework.Process.{ AbortCommand, AbortEvent }

import scala.reflect._

object PersistentProcess {
  case class Perform[State](action: ((ActorContext, State)) ⇒ Unit)
}

trait Customizable {
  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior
}

trait AbortablePersistentProcess[S] extends PersistentProcess[S] {
  def createAbortEvent(): AbortEvent
  override def eventHandling: Receive = {
    case event: AbortEvent ⇒
      process.abort()
      aborted = true
  }
  override def commandHandling: Receive = {
    case abort: AbortCommand ⇒
      persist(createAbortEvent()) { event ⇒
        process.abort()
        aborted = true
        sender() ! event
      }
  }
}

abstract class PersistentProcess[State: ClassTag] extends PersistentActor with ActorLogging {
  def process: ProcessStep[State]
  var state: State

  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior
  def unhandledRecoveryEvent: PartialFunction[Process.Event, Unit] = PartialFunction.empty

  var aborted = false
  final def receiveRecover: Receive = eventHandling orElse {
    case event: Process.Event if process.handleUpdateState.isDefinedAt(event) ⇒
      state = process.handleUpdateState(event)(state)
    case event: Process.Event =>
      log.warning(s"Persistent process ({}): unhandled event during recovery, event='{}'", getClass.getSimpleName, event)
      unhandledRecoveryEvent(event)
    case RecoveryCompleted ⇒
      import context.dispatcher
      if (!aborted)
        process.run()
  }

  def receiveCommand: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case x if commandHandling.isDefinedAt(x) =>
      log.debug(s"Persistent process ({}): commandHandling handles command '{}'", getClass.getSimpleName, x)
      commandHandling(x)
    case x if process.handleReceiveCommand.isDefinedAt(x) ⇒
      val event = process.handleReceiveCommand(x)
      log.debug(s"Persistent process ({}): handled command '{}', resulted in event '{}'", getClass.getSimpleName, x, event)
      self ! event
    case event: Process.Event if (process.handleUpdateState.isDefinedAt(event)) =>
      persist(event) { event =>
        log.debug(s"Persistent process ({}): persisted event '{}'", getClass.getSimpleName, event)
        state = process.handleUpdateState(event)(state)
      }
    case event: Process.Event =>
      log.debug(s"Persistent process ({}): unable to persist event '{}', probably the event was already persisted before or the event is (now) unknown to the process.", getClass.getSimpleName, event)
    case processframework.Process.GetState =>
      log.debug(s"Persistent process ({}): get state '{}'", getClass.getSimpleName, state)
      sender() ! state
    case perform: PersistentProcess.Perform[State] =>
      log.debug(s"Persistent process ({}): performing action, '{}'", getClass.getSimpleName, perform.action)
      perform.action(context, state)
    case m =>
      log.debug(s"Persistent process ({}): persistent process, unhandled msg: '{}'", getClass.getSimpleName, m)
      super.unhandled(m)
  }
}
