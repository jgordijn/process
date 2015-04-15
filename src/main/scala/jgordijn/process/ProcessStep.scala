package jgordijn.process

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

trait ProcessStep[State, NewState] {
  implicit def context: ActorContext
  private[process] val promise: Promise[NewState] = Promise()

  type Execution = State => Unit
  type UpdateFunction = PartialFunction[Process.Event, Any => Unit]
  type CommandToEvent = PartialFunction[Any, Process.Event]

  def execute()(implicit process: ActorRef): Execution
  def receiveCommand: CommandToEvent
  def updateState: UpdateFunction

  def retryInterval: Duration = Duration.Inf

  final def isCompleted = promise.isCompleted
  final def markDone(newState: NewState): Unit = promise.trySuccess(newState)
  final def onComplete(completeFn: ((ActorContext, State)) => Unit)(implicit executionContext: ExecutionContext, process: ActorRef): Unit =
    promise.future.foreach{ _ => process ! PersistentProcess.Perform(completeFn) }

  final def ~>[OtherNewState](next: ProcessStep[NewState, OtherNewState])(implicit context: ActorContext): ProcessStep[State, OtherNewState] = new Chain(this, next: _*)

  private[process] def run(state: State)(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[State]): Future[NewState
] = runImpl(state)
  private val innerActor = context.actorOf(Props(new Actor {
    def receive = {
      case msg if receiveCommand.isDefinedAt(msg) =>
        val event = receiveCommand(msg)
        context.parent ! event
    }
  }))
  private[process] def handleUpdateState: UpdateFunction = if(isCompleted) PartialFunction.empty[Process.Event, Any => Unit] else updateState
  private[process] def handleReceiveCommand: CommandToEvent = if(isCompleted) PartialFunction.empty[Any, Process.Event] else receiveCommand
  private[process] def executeWithPossibleRetry()(implicit process: ActorRef): Execution = { state =>
    implicit val _ = context.dispatcher
    if(retryInterval.isFinite())
      context.system.scheduler.scheduleOnce(Duration.fromNanos(retryInterval.toNanos)) { if (!isCompleted) executeWithPossibleRetry()(process)(state) }
    execute()(process)(state)
  }
  private[process] def runImpl(state: State)(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[State]): Future[NewState] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!isCompleted) executeWithPossibleRetry()(innerActor)(state)
    promise.future
  }
}
