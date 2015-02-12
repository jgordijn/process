package process

import akka.actor._
import akka.pattern.pipe
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent._
import scala.concurrent.Promise

object Main extends App {
  val system = ActorSystem("ProcessEasy")
  val process = system.actorOf(WalkInThePark.props, "processEasy")
  //process ! ProcessEasy.Start(new java.util.Date)
  println("Started")
  Thread.sleep(500)
  process  ! "Start"
  Thread.sleep(5000)
  system.shutdown()

}

trait ProcessStep {
  def promise: Promise[Unit] = Promise[Unit]()
  def ~>(next: ProcessStep*): ProcessStep = new Chain(this, next:_*)
  def execute()(implicit executionContext: ExecutionContext): Future[Any]
  def complete: PartialFunction[Any, Unit]
}

class Chain(a: ProcessStep, b: ProcessStep*) extends ProcessStep {
  def execute()(implicit executionContext: ExecutionContext) = a.execute flatMap(_ => Future.sequence(b.map(_.execute)).map(_ => ()))
  def complete: PartialFunction[Any, Unit] = a.complete orElse b.foldRight(PartialFunction.empty[Any, Unit]){case (x, y) => x.complete orElse y}
}

case class PrintStep(output: String, sleep: Long) extends ProcessStep {
  def execute()(implicit executionContext: ExecutionContext) = future {
    println(s"${new java.util.Date} Step is executed with output: $output ($sleep)")
    Thread.sleep(sleep)
    promise.trySuccess(())
  }
  def complete = PartialFunction.empty
}

object WalkInThePark {
  def props = Props(new WalkInThePark)
}

class WalkInThePark extends Actor {
  var nrOfCalls = 0
  def process = PrintStep("Stap 1", 500) ~> (PrintStep("Stap 2", 1500), PrintStep("Stap 3", 100)) ~> PrintStep("Done", 50)
  import context.dispatcher

  def receive = {
    case "Start" =>
      process.execute()
  }
}
