package jgordijn.process

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

object Choice {
  case class MakeChoice(result: Boolean)
  case class ChoiceResult(result: Boolean) extends Process.Event
}
class Choice[S](condition: S => Boolean, processIfTrue: ProcessStep[S], processIfFalse: ProcessStep[S])(implicit val context: ActorContext, classTag: ClassTag[S]) extends ProcessStep[S] {
  import context.dispatcher
  private[process] val truePromise: Promise[Unit] = Promise[Unit]()
  private[process] val falsePromise: Promise[Unit] = Promise[Unit]()

  def receiveCommand: CommandToEvent = {
    if(truePromise.isCompleted) processIfTrue.receiveCommand
    else if(falsePromise.isCompleted) processIfFalse.receiveCommand
    else receiveCommandChoice
  }

  def updateState: UpdateFunction = {
    if(truePromise.isCompleted) processIfTrue.updateState
    else if(falsePromise.isCompleted) processIfFalse.updateState
    else updateStateChoice
  }

  override private[process] def runImpl()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    val trueFlow = truePromise.future flatMap { _ =>
      processIfTrue.run()
    }
    val falseFlow = falsePromise.future flatMap { _ =>
      processIfFalse.run()
    }
    super.runImpl()
    Future.firstCompletedOf(List(trueFlow, falseFlow))
  }

  def execute()(implicit process: ActorRef): Execution = { state =>
    process ! Choice.MakeChoice(condition(state))
  }
  def receiveCommandChoice: CommandToEvent = {
    case Choice.MakeChoice(value) =>
      Choice.ChoiceResult(value)
  }
  def updateStateChoice: UpdateFunction = {
    case Choice.ChoiceResult(true) => state => {
      truePromise.trySuccess(())
      state
    }
    case Choice.ChoiceResult(false) => state => {
      falsePromise.trySuccess(())
      state
    }
  }
}
