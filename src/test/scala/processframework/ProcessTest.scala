package processframework

import akka.actor.{ ActorContext, ActorRef, Props }
import akka.testkit.TestProbe

import scala.concurrent.duration._

object ProcessTest {
  case object Start
  case object Response
  case class Command(i: Int)
  case object Completed extends Process.Event

  class MockStep(service: ActorRef, retryInt: Duration)(implicit val context: ActorContext) extends ProcessStep[Int] {
    override val retryInterval = retryInt
    def execute()(implicit process: akka.actor.ActorRef) = { state ⇒
      service ! Command(state)
    }
    def receiveCommand = {
      case Response ⇒
        Completed
    }
    def updateState = {
      case Completed ⇒ state ⇒
        markDone(state + 1)
    }
  }

  class Process1(service: ActorRef, retryInterval: Duration) extends Process[Int] {
    import context.dispatcher
    var state = 0
    val process = new MockStep(service, retryInterval)

    def receive = {
      case Start ⇒
        process.run()
    }
  }
}

class ProcessTest extends BaseSpec {
  import ProcessTest._

  "Process" should {
    "have a happy flow" in {
      val service = TestProbe()
      val process = system.actorOf(Props(new Process1(service.ref, Duration.Inf)), "Process1")
      process ! processframework.Process.GetState
      expectMsg(0)
      process ! Start

      service.expectMsg(Command(0))
      service.reply(Response)

      eventually {
        process ! processframework.Process.GetState
        expectMsg(1)
      }
      process ! Start
      expectNoMsg(250 millis)
      process ! processframework.Process.GetState
      expectMsg(1)
    }

    "does not retry by default" in {
      val service = TestProbe()
      val process = system.actorOf(Props(new Process1(service.ref, Duration.Inf)), "Process2")
      process ! processframework.Process.GetState
      expectMsg(0)
      process ! Start

      service.expectMsg(Command(0))
      expectNoMsg()
    }

    "retries execution until succeeded" in {
      val service = TestProbe()
      val process = system.actorOf(Props(new Process1(service.ref, 150 millis)), "Process3")
      process ! processframework.Process.GetState
      expectMsg(0)
      process ! Start

      service.expectMsg(Command(0))
      service.expectMsg(1000.millis, Command(0))
      service.expectMsg(1000.millis, Command(0))
      service.reply(Response)
      expectNoMsg()
    }
  }
}
