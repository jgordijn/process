package processframework

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

trait ProcessStep[S] {
  type Execution = S ⇒ Unit
  type UpdateFunction = PartialFunction[Process.Event, S ⇒ S]
  type CommandToEvent = PartialFunction[Any, Process.Event]

  implicit def context: ActorContext

  private[processframework] val promise: Promise[Unit] = Promise[Unit]()
  private val innerActor = context.actorOf(Props(new Actor {
    def receive = {
      case msg if receiveCommand.isDefinedAt(msg) ⇒
        val event = receiveCommand(msg)
        context.parent ! event
    }
  }))

  def execute()(implicit process: ActorRef): Execution
  def receiveCommand: CommandToEvent
  def updateState: UpdateFunction

  def retryInterval: Duration = Duration.Inf

  final def isCompleted = isStepCompleted()
  final def markDone(): Unit = promise.trySuccess(())
  final def markDone(state: S): S = {
    markDone()
    state
  }
  final def onComplete(completeFn: ((ActorContext, S)) ⇒ Unit)(implicit executionContext: ExecutionContext, process: ActorRef): Unit =
    promise.future.foreach { _ ⇒ process ! PersistentProcess.Perform(completeFn) }
  final def onCompleteAsync(completeFn: ⇒ Unit)(implicit executionContext: ExecutionContext): Unit = promise.future.foreach(_ ⇒ completeFn)
  final def ~>(next: ProcessStep[S]*)(implicit context: ActorContext): ProcessStep[S] = new Chain(this, next: _*)

  private[processframework] def isStepCompleted() = promise.isCompleted
  private[processframework] def abort(): Unit = promise.tryFailure(new RuntimeException("Process aborted"))
  private[processframework] def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = runImpl
  private[processframework] def handleUpdateState: UpdateFunction = if (isCompleted) PartialFunction.empty[Process.Event, S ⇒ S] else updateState
  private[processframework] def handleReceiveCommand: CommandToEvent = if (isCompleted) PartialFunction.empty[Any, Process.Event] else receiveCommand
  private[processframework] def executeWithPossibleRetry()(implicit process: ActorRef): Execution = { state ⇒
    implicit val _ = context.dispatcher
    if (retryInterval.isFinite())
      context.system.scheduler.scheduleOnce(Duration.fromNanos(retryInterval.toNanos)) { if (!isCompleted) executeWithPossibleRetry()(process)(state) }
    execute()(process)(state)
  }
  private[processframework] def runImpl()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!isCompleted) (process ? Process.GetState).mapTo[S].foreach(executeWithPossibleRetry()(innerActor))
    promise.future
  }
}
