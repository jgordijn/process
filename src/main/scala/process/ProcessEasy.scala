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
  def execute()(implicit process: ActorRef): S => Unit
  def complete: PartialFunction[Any, Unit]
  def doComplete: PartialFunction[Any, Unit] = {
    case x if !future.isCompleted => complete(x)
  }

  def ~>(next: ProcessStep[S]*): ProcessStep[S] = new Chain(this, next: _*)
  def run()(implicit process: ActorRef, executionContext: ExecutionContext, classTag: ClassTag[S]): Future[Unit] = {
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout: Timeout = 5 seconds

    if (!promise.isCompleted) (process ? Process.GetState).mapTo[S].foreach(execute())
    future
  }
}

class EchoActor extends Actor with ActorLogging {
  import scala.concurrent.duration._
  import context.dispatcher
  def receive = {
    case x =>
      log.debug(s"Received: $x")
      context.system.scheduler.scheduleOnce(2 seconds, sender(), x)
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
  def execute()(implicit process: ActorRef) = throw new UnsupportedOperationException("This is a chain. It does not execute by itself. Please invoke run.")
  def complete: PartialFunction[Any, Unit] = a.doComplete orElse b.foldRight(PartialFunction.empty[Any, Unit]) { case (x, y) => x.doComplete orElse y }
}

case class Completed(message: String)
case class EchoStep(echoer: ActorRef) extends ProcessStep[Int] {
  def execute()(implicit process: ActorRef) = state => {
    echoer ! s"This is my message: $state"
  }
  def complete = {
    case retVal: String if retVal.contains("This is my message") =>
      println(s"${new java.util.Date} Completing this $this")
      promise.success(())
  }
}

case class PrintStep(output: String, sleep: Long) extends ProcessStep[Int] {
  def execute()(implicit process: ActorRef) = state => {
    println(s"${new java.util.Date} Step is executed with output: $output ($sleep) STATE: $state ${future.isCompleted}")
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
  val echoer = context.actorOf(Props(new EchoActor), "echoer")
  val echo = EchoStep(echoer)
  val echo2 = EchoStep(echoer)


  val process: ProcessStep[Int] =
    step1 ~> step2 ~> echo ~> echo2  ~> (PrintStep("Stap 2", 2500), PrintStep("Stap 3", 100)) ~> PrintStep("Done", 50)

  process.future.foreach { _ =>
    context.system.shutdown()
  }

  def receive = {
    case "Start" =>
      println("START")
      process.run()
    case Process.GetState => sender() ! nrOfCalls
    case msg =>
      process.doComplete(msg)
  }
}
