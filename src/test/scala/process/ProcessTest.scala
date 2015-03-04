package process

import akka.actor.ActorContext
import scala.concurrent.duration._
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers._
import org.scalatest.concurrent.Eventually

object ProcessTest {
  case object Start
  case object Response
  case class Command(i: Int)
  case object Completed extends Process.Event
  class MockStep(service: ActorRef)(implicit val context: ActorContext) extends ProcessStep[Int] {
    def execute()(implicit process: akka.actor.ActorRef): Int => Unit = { state =>
      service ! Command(state)
    }
    def receiveCommand: PartialFunction[Any,Process.Event] = {
      case Response =>
        Completed
    }
    def updateState: PartialFunction[Process.Event,Int => Int] = {
      case Completed => { state =>
        markDone()
        state + 1
      }
    }
  }

  class Process1(service: ActorRef) extends Process[Int] {
    import context.dispatcher
    val ac = context
    var state = 0
    val process = new MockStep(service)

    def receive = {
      case Start =>
        process.run()
    }
  }
}
class ProcessTest extends TestKit(ActorSystem("ProcessStepTest"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {
  import ProcessTest._
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Process" should {
    "have a happy flow" in {
      val service = TestProbe()
      val process = system.actorOf(Props(new Process1(service.ref)), "Process1")
      process ! Process.GetState
      expectMsg(0)
      process ! Start

      service.expectMsg(Command(0))
      service.reply(Response)

      eventually {
        process ! Process.GetState
        expectMsg(1)
      }
      process ! Start
      expectNoMsg(250 millis)
      process ! Process.GetState
      expectMsg(1)

    }
  }
}
