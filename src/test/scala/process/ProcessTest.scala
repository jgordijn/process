package process

import scala.concurrent.duration._
import akka.actor.Props
import akka.actor.{ ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import matchers.ShouldMatchers._
import org.scalatest.concurrent.Eventually

class ProcessTest extends TestKit(ActorSystem("ProcessStepTest"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  case object Start
  case object Response
  case class Command(i: Int)
  case object Completed extends Process.Event
  class Process1 extends Process[Int] {
    import context.dispatcher

    var state = 0
    val process = new ProcessStep[Int] {
      def execute()(implicit process: akka.actor.ActorRef): Int => Unit = { state =>
        testActor.tell(Command(state), process)
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
    def receive = {
      case Start =>
        process.run()
    }
  }

  "Process" should {
    "have a happy flow" in {
      val process = system.actorOf(Props(new Process1), "Process1")
      process ! Process.GetState
      expectMsg(0)
      process ! Start
      expectMsg(Command(0))
      process ! Response
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
