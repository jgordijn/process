package jgordijn.process

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ ActorContext, ActorRef }

class Choice[S](condition: S ⇒ Boolean, processIfTrue: ProcessStep[S], processIfFalse: ProcessStep[S])(implicit val context: ActorContext, classTag: ClassTag[S]) extends ProcessStep[S] {

  private[process] val truePromise: Promise[Unit] = Promise[Unit]()
  private[process] val falsePromise: Promise[Unit] = Promise[Unit]()
  var result = Option.empty[Boolean]

  def receiveCommand: CommandToEvent = {
    if (truePromise.isCompleted) processIfTrue.receiveCommand
    else if (falsePromise.isCompleted) processIfFalse.receiveCommand
    else PartialFunction.empty
  }

  def updateState: UpdateFunction = {
    case event if processIfFalse.handleUpdateState.isDefinedAt(event) || processIfTrue.handleUpdateState.isDefinedAt(event) ⇒
      result match {
        case Some(true) ⇒
          truePromise.trySuccess(())
          processIfTrue.updateState.apply(event)
        case Some(false) ⇒
          falsePromise.trySuccess(())
          processIfFalse.updateState.apply(event)
        case None ⇒ { state ⇒
          result = Some(condition(state))
          updateState.apply(event)(state)
        }
      }
  }

  override private[process] def runImpl()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    val trueFlow = truePromise.future flatMap { _ ⇒
      processIfTrue.run()
    }
    val falseFlow = falsePromise.future flatMap { _ ⇒
      processIfFalse.run()
    }
    super.runImpl()
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
