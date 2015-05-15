package processframework

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext

import akka.actor.{ ActorContext, ActorRef }

private[processframework] class Chain[S](a: ProcessStep[S], b: ProcessStep[S]*)(implicit val context: ActorContext) extends ProcessStep[S] {

  override private[processframework] def abort(): Unit = {
    a.abort()
    b.foreach(_.abort())
    super.abort()
  }

  override private[processframework] def runImpl()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    a.run() flatMap { _ ⇒
      Future.sequence(b.map(_.run())).flatMap { _ ⇒
        markDone()
        promise.future
      }
    }
  }
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")

  def receiveCommand: CommandToEvent = a.handleReceiveCommand orElse b.foldRight(PartialFunction.empty[Any, Process.Event]) { case (x, y) ⇒ x.handleReceiveCommand orElse y }
  def updateState: UpdateFunction = a.handleUpdateState orElse b.foldRight(PartialFunction.empty[Process.Event, S ⇒ S]) { case (x, y) ⇒ x.handleUpdateState orElse y }
}
