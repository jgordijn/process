package process

import akka.actor._
import akka.pattern.pipe
import akka.util.Timeout
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent._
import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success

object Main extends App {
  val system = ActorSystem("ProcessEasy")
  val process = system.actorOf(WalkInThePark.props, "processEasy")
  println("Started")
  Thread.sleep(500)
  process ! "Start"
  import system.dispatcher
  import scala.concurrent.duration._
  try { system.awaitTermination(10 seconds) }
  catch {
    case _: TimeoutException =>
      system.shutdown()
  }
}

object Process {
  case class Perform[S](action: S ⇒ Unit)
  case object GetState
}

trait ProcessStep[S] {
  val promise: Promise[Unit] = Promise[Unit]()
  val future = promise.future
  def execute(): S => Unit
  def complete: PartialFunction[Any, Unit]

  def ~>(next: ProcessStep[S]*): ProcessStep[S] = new Chain(this, next: _*)
  def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!promise.isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute())
    future
  }
}

class Chain[S](a: ProcessStep[S], b: ProcessStep[S]*) extends ProcessStep[S] {
  override def run()(implicit self: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    a.run() flatMap { _ =>
      Future.sequence(b.map(_.run())).flatMap { _ =>
        promise.trySuccess(())
        future
      }
    }
  }
  def execute() = throw new UnsupportedOperationException("Please invoke run")
  def complete: PartialFunction[Any, Unit] = a.complete orElse b.foldRight(PartialFunction.empty[Any, Unit]) { case (x, y) => x.complete orElse y }
}

case class PrintStep(output: String, sleep: Long) extends ProcessStep[Int] {
  def execute() = state => {
    println(s"${new java.util.Date} Step is executed with output: $output ($sleep) STATE: $state")
    Thread.sleep(sleep)
    promise.success(())
  }
  def complete = PartialFunction.empty
}

object WalkInThePark {
  def props = Props(new WalkInThePark)
}

class WalkInThePark extends Actor {
  import context.dispatcher
  var nrOfCalls = 0
  val step1 = PrintStep("Stap 1", 500)
  val step2 = PrintStep("FOOOOO", 300)

  val process: ProcessStep[Int] =
    step1 ~> step2 ~> (PrintStep("Stap 2", 1500), PrintStep("Stap 3", 100)) ~> PrintStep("Done", 50)

  process.future.foreach { _ =>
    context.system.shutdown()
  }

  def receive = {
    case "Start" =>
      println("START")
      process.run()
    case Process.GetState => sender() ! nrOfCalls
  }
}
