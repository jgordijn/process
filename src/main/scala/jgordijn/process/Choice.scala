package jgordijn.process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ ActorContext, ActorRef }

class Choice[S, NewState](condition: S ⇒ Boolean, processIfTrue: ProcessStep[S, NewState], processIfFalse: ProcessStep[S, NewState])(implicit val context: ActorContext, classTag: ClassTag[S]) extends ProcessStep[S, NewState] {

  private[process] val truePromise: Promise[Unit] = Promise[Unit]()
  private[process] val falsePromise: Promise[Unit] = Promise[Unit]()
  var result = Option.empty[Boolean]

  def receiveCommand: CommandToEvent = {
    if (truePromise.isCompleted) processIfTrue.receiveCommand
    else if (falsePromise.isCompleted) processIfFalse.receiveCommand
    else PartialFunction.empty
  }

  def updateState: UpdateFunction = {
    case event ⇒
      result match {
        case Some(true) ⇒
          truePromise.trySuccess(())
          processIfTrue.updateState.apply(event)
        case Some(false) ⇒
          falsePromise.trySuccess(())
          processIfFalse.updateState.apply(event)
        case None ⇒ { case state: S ⇒
          result = Some(condition(state))
          updateState
        }
      }
  }

  override private[process] def runImpl(state: S)(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    val trueFlow = truePromise.future flatMap { _ ⇒
      processIfTrue.run(state)
    }
    val falseFlow = falsePromise.future flatMap { _ ⇒
      processIfFalse.run(state)
    }
    super.runImpl(state)
    Future.firstCompletedOf(List(trueFlow, falseFlow))
  }

  def execute()(implicit process: ActorRef): Execution = { state ⇒
    val choiceResult = condition(state)
    result = Some(choiceResult)
    if (choiceResult)
      truePromise.trySuccess(())
    else
      falsePromise.trySuccess(())
  }
}
