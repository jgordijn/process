package jgordijn.process

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext

import akka.actor.{ActorContext, ActorRef}


private[process] class Chain[FromState, IntermediateState, ToState](a: ProcessStep[FromState, IntermediateState], b: ProcessStep[IntermediateState, ToState])(implicit val context: ActorContext) extends ProcessStep[FromState, ToState] {
  override private[process] def runImpl(state: FromState)(implicit self: ActorRef, executionContext: ExecutionContext, classTagA: ClassTag[FromState], classTagB: ClassTag[IntermediateState], classTagC: ClassTag[ToState]): Future[ToState] = {
    a.run(state) flatMap { intermediateState =>
      b.run(intermediateState).flatMap { toState =>
        markDone(toState)
        promise.future
      }
    }
  }
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")

  def receiveCommand: CommandToEvent = a.handleReceiveCommand orElse b.handleReceiveCommand
  def updateState: UpdateFunction = a.handleUpdateState orElse b.handleUpdateState
}
