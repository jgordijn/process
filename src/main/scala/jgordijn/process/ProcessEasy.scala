package jgordijn.process

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

class EchoActor extends Actor with ActorLogging {
  import scala.concurrent.duration._
  import context.dispatcher
  def receive = {
    case x =>
      log.debug(s"Received: $x")
      context.system.scheduler.scheduleOnce(2 seconds, sender(), x)
  }
}


//case class Completed(message: String)
case object Echoed extends Process.Event
case class EchoStep(echoer: ActorRef)(implicit val context: ActorContext) extends ProcessStep[Int, Int] {
  def execute()(implicit process: ActorRef) = state => {
    println(s"Execute echostep: $state")
    echoer ! s"This is my message: $state"
  }

  def receiveCommand = {
    case retVal: String if retVal.contains("This is my message") =>
      println(s"${new java.util.Date} Completing this $this")
      Echoed
  }

  def updateState = {
    case Echoed => x =>
      println("Complete echo")
      markDone(x + 1)
  }
}

case class PrintStep(output: String, sleep: Long)(implicit val context: ActorContext) extends ProcessStep[Int] {
  def execute()(implicit process: ActorRef) = state => {
    println(s"${new java.util.Date} Step is executed with output: $output ($sleep) STATE: $state ${promise.future.isCompleted}")
    Thread.sleep(sleep)
    markDone()
  }
  def receiveCommand = PartialFunction.empty
  def updateState = PartialFunction.empty
}

object WalkInThePark {
  def props = Props(new WalkInThePark)
}


class WalkInThePark extends Process[Int] {
  import context.dispatcher
  var state = 0
  val step1 = PrintStep("Stap 1", 500)
  val step2 = PrintStep("FOOOOO", 300)
  val echoer = context.actorOf(Props(new EchoActor), "echoer")
  val echo = EchoStep(echoer)
  val echo2 = EchoStep(echoer)

  val process: ProcessStep[Int] =
    step1 ~> step2 ~> echo ~> echo2  ~> (PrintStep("Stap 2", 2500), PrintStep("Stap 3", 100)) ~> PrintStep("Done", 50)

  process.promise.future.foreach { _ =>
    context.system.shutdown()
 }

  def receive = {
    case "Start" =>
      println("START")
      process.run()
  }
}
