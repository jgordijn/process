package jgordijn.process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout

trait ProcessStep[S] {
  implicit def context: ActorContext
  private[process] val promise: Promise[Unit] = Promise[Unit]()

  def execute()(implicit process: ActorRef): S => Unit
  def receiveCommand: PartialFunction[Any, Process.Event]
  def updateState: PartialFunction[Process.Event, S => S]

  final def isCompleted = promise.isCompleted
  final def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = runImpl
  final def markDone(): Unit = promise.trySuccess(())
  final def onComplete(completeFn: => Unit)(implicit executionContext: ExecutionContext): Unit = promise.future.foreach{ _ => completeFn }

  final def ~>(next: ProcessStep[S]*)(implicit context: ActorContext): ProcessStep[S] = new Chain(this, next: _*)

  private val innerActor = context.actorOf(Props(new Actor {
    def receive = {
      case msg if receiveCommand.isDefinedAt(msg) =>
        val event = receiveCommand(msg)
        context.parent ! event
    }
  }))
  private[process] def handleUpdateState: PartialFunction[Process.Event, S => S] = if(isCompleted) PartialFunction.empty[Process.Event, S => S] else updateState
  private[process] def handleReceiveCommand: PartialFunction[Any, Process.Event] = if(isCompleted) PartialFunction.empty[Any, Process.Event] else receiveCommand
  private[process] def runImpl()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute()(innerActor))
    promise.future
  }
}
