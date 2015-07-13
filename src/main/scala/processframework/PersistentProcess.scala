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
        postPersist(event)
        process.abort()
        aborted = true
        sender() ! event
      }
  }
}

abstract class PersistentProcess[State: ClassTag] extends PersistentActor with ActorLogging {
  var state: State
  var aborted = false

  def process: ProcessStep[State]

  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior
  def unhandledRecoveryEvent: PartialFunction[Process.Event, Unit] = PartialFunction.empty

  /**
   * Hook to give the implementer the power to do something after the fact that an event is persisted.
   *
   * @param event The event which is persisted in the journal
   */
  def postPersist(event: Process.Event): Unit = {}

  final def receiveRecover: Receive = eventHandling orElse {
    case event: Process.Event if process.handleUpdateState.isDefinedAt(event) ⇒
      state = process.handleUpdateState(event)(state)
    case event: Process.Event ⇒
      log.warning(s"Persistent process ({}): unhandled event during recovery, event='{}'", getClass.getSimpleName, event)
      unhandledRecoveryEvent(event)
    case RecoveryCompleted ⇒
      import context.dispatcher
      if (!aborted)
        process.run()
  }

  def receiveCommand: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case cmd if commandHandling.isDefinedAt(cmd) ⇒
      log.debug(s"Persistent process ({}): commandHandling handles command '{}'", getClass.getSimpleName, cmd)
      commandHandling(cmd)
    case cmd if process.handleReceiveCommand.isDefinedAt(cmd) ⇒
      val event = process.handleReceiveCommand(cmd)
      log.debug(s"Persistent process ({}): handled command '{}', resulted in event '{}'", getClass.getSimpleName, cmd, event)
      self ! event
    case event: Process.Event if process.handleUpdateState.isDefinedAt(event) ⇒
      persist(event) { evt ⇒
        log.debug(s"Persistent process ({}): persisted event '{}'", getClass.getSimpleName, evt)
        postPersist(evt)
        state = process.handleUpdateState(evt)(state)
      }
    case event: Process.Event ⇒
      log.debug(s"Persistent process ({}): unable to persist event '{}', probably the event was already persisted before or the event is (now) unknown to the process.", getClass.getSimpleName, event)
    case Process.GetState ⇒
      log.debug(s"Persistent process ({}): get state '{}'", getClass.getSimpleName, state)
      sender() ! state
    case perform: PersistentProcess.Perform[State] ⇒
      log.debug(s"Persistent process ({}): performing action, '{}'", getClass.getSimpleName, perform.action)
      perform.action(context, state)
    case other ⇒
      log.debug(s"Persistent process ({}): persistent process, unhandled msg: '{}'", getClass.getSimpleName, other)
      super.unhandled(other)
  }
}
