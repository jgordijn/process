package jgordijn.process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout

trait ProcessStep[S] {
  implicit def context: ActorContext
  val promise: Promise[Unit] = Promise[Unit]()
  def isCompleted = promise.isCompleted
  def execute()(implicit process: ActorRef): S => Unit

  def receiveCommand: PartialFunction[Any, Process.Event]
  def updateState: PartialFunction[Process.Event, S => S]
  def handleUpdateState: PartialFunction[Process.Event, S => S] = if(promise.future.isCompleted) PartialFunction.empty[Process.Event, S => S] else updateState
  def handleReceiveCommand: PartialFunction[Any, Process.Event] = if(promise.future.isCompleted) PartialFunction.empty[Any, Process.Event] else receiveCommand
  def markDone(): Unit = promise.trySuccess(())

  val innerActor = context.actorOf(Props(new Actor {
    def receive = {
      case msg if receiveCommand.isDefinedAt(msg) =>
        val event = receiveCommand(msg)
        context.parent ! event
    }
  }))

  def ~>(next: ProcessStep[S]*)(implicit context: ActorContext): ProcessStep[S] = new Chain(this, next: _*)
  def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!promise.isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute()(innerActor))
    promise.future
  }
}
