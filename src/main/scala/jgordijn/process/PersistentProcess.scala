package jgordijn.process

import akka.actor.Actor._
import jgordijn.process.Process.{AbortCommand, AbortEvent}

import scala.reflect._

import akka.actor.{ Actor, ActorContext }
import akka.persistence.{ PersistentActor, RecoveryCompleted }

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

abstract class PersistentProcess[State : ClassTag] extends PersistentActor {
  def process: ProcessStep[State]
  var state: State

  def eventHandling: Receive = Actor.emptyBehavior
  def commandHandling: Receive = Actor.emptyBehavior

  var aborted = false
  final def receiveRecover: Receive = eventHandling orElse {
    case event: Process.Event =>
      state = process.handleUpdateState(event)(state)
    case RecoveryCompleted =>
      import context.dispatcher
      if(!aborted)
        process.run()
  }

  def receiveCommand: Receive = Actor.emptyBehavior

  override def unhandled(msg: Any): Unit = msg match {
    case x if commandHandling.isDefinedAt(x) =>
      commandHandling(x)
    case x if process.handleReceiveCommand.isDefinedAt(x) =>
      val event = process.handleReceiveCommand(x)
      self ! event
    case event: Process.Event =>
      persist(event) { event =>
        state = process.handleUpdateState(event)(state)
      }
    case Process.GetState =>
      sender() ! state
    case perform: PersistentProcess.Perform[State] =>
      perform.action(context, state)
    case m =>
      super.unhandled(m)
  }
}
