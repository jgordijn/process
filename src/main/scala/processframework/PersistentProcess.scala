package processframework

import akka.actor.Actor._
import akka.actor.{ActorLogging, Actor, ActorContext}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import processframework.Process.{AbortCommand, AbortEvent}

import scala.reflect._

object PersistentProcess {
  case class Perform[State](action: ((ActorContext, State)) => Unit)
}

trait Customizable {
  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior
}

trait AbortablePersistentProcess[S] extends PersistentProcess[S] {
  def createAbortEvent(): AbortEvent
  override def eventHandling: Receive = {
    case event : AbortEvent =>
      process.abort()
      aborted = true
  }
  override def commandHandling: Receive = {
    case abort: AbortCommand =>
      persist(createAbortEvent()) { event =>
        process.abort()
        aborted = true
        sender() ! event
      }
  }
}

abstract class PersistentProcess[State : ClassTag] extends PersistentActor with ActorLogging {
  def process: ProcessStep[State]
  var state: State

  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior
  def unhandledRecoveryEvent: PartialFunction[Process.Event, Unit] = PartialFunction.empty

  var aborted = false
  final def receiveRecover: Receive = eventHandling orElse {
    case event: Process.Event if process.handleUpdateState.isDefinedAt(event) =>
      state = process.handleUpdateState(event)(state)
    case event: Process.Event =>
      log.warning(s"Persistent process (${this.getClass.getSimpleName}): unhandled event during recovery, event='$event'")
      unhandledRecoveryEvent(event)
    case RecoveryCompleted =>
      import context.dispatcher
      if(!aborted)
        process.run()
  }

  def receiveCommand: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case x if commandHandling.isDefinedAt(x) =>
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): commandHandling handles command '$x'")
      commandHandling(x)
    case x if process.handleReceiveCommand.isDefinedAt(x) =>
      val event = process.handleReceiveCommand(x)
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): handled command '$x', resulted in event '$event'")
      self ! event
    case event: Process.Event if (process.handleUpdateState.isDefinedAt(event)) =>
      persist(event) { event =>
        log.debug(s"Persistent process (${this.getClass.getSimpleName}): persisted event '$event'")
        state = process.handleUpdateState(event)(state)
      }
    case event: Process.Event =>
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): unable to persist event '$event', probably the event was already persisted before or the event is (now) unknown to the process.")
    case processframework.Process.GetState =>
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): get state '$state'")
      sender() ! state
    case perform: PersistentProcess.Perform[State] =>
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): performing action, '${perform.action}'")
      perform.action(context, state)
    case m =>
      log.debug(s"Persistent process (${this.getClass.getSimpleName}): persistent process, unhandled msg: '$m'")
      super.unhandled(m)
  }
}
