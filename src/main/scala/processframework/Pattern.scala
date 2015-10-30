package processframework

import akka.actor.{ ActorContext, ActorRef }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

class Pattern[P, S](matchFunction: S ⇒ P, nextProcessStep: PartialFunction[P, ProcessStep[S]])(implicit val context: ActorContext, classTag: ClassTag[S]) extends ProcessStep[S] {

  private var processStep: ProcessStep[S] = null

  override def execute()(implicit process: ActorRef): Execution = { state ⇒
    val pattern = matchFunction(state)
    processStep = nextProcessStep.lift(pattern).getOrElse(EmptyStep())
  }

  override def receiveCommand: CommandToEvent = processStep.handleReceiveCommand

  override def updateState: UpdateFunction = {
    case event if processStep.handleUpdateState.isDefinedAt(event) ⇒
      processStep.updateState.apply(event)
  }

  override private[processframework] def abort(): Unit = {
    processStep.abort()
    super.abort()
  }

  override private[processframework] def runImpl()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    super.runImpl()
    processStep.run()
  }

  override private[processframework] def isStepCompleted() =
    if (processStep == null) false
    else processStep.isCompleted
}