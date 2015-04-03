package jgordijn.process

import akka.actor.ActorContext
import akka.actor.ActorContext
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.persistence.RecoveryCompleted
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers._
import org.scalatest.concurrent.Eventually

object PersistentProcessTest {
  case object Start
  case object Response
  case class Command(i: Int)
  case object Completed extends Process.Event
  class Step(probe: ActorRef)(implicit val context: ActorContext) extends ProcessStep[Int] {
    def execute()(implicit process: akka.actor.ActorRef): Execution = { state =>
      probe ! Command(state)
    }
    def receiveCommand: CommandToEvent = {
      case Response =>
        Completed
    }
    def updateState: UpdateFunction = {
      case Completed => { state =>
        markDone()
        state + 1
      }
    }
  }

  class InitStep()(implicit val context: ActorContext) extends ProcessStep[Int] {
    def execute()(implicit process: akka.actor.ActorRef): Execution = { state =>
    }
    def receiveCommand: PartialFunction[Any,Process.Event] = {
      case Start =>
        Completed
    }

    def updateState: UpdateFunction = {
      case Completed => { state =>
        markDone()
        state
      }
    }
  }


  class PersistentProcess1(probe1: TestProbe, probe2: TestProbe, probe3: TestProbe, probe4: TestProbe, endProbe: TestProbe, completeHook: TestProbe) extends PersistentProcess[Int] {
    import context.dispatcher
    val persistenceId = "PersistentProcess1"

    var state = 0
    val process = new InitStep() ~> new Step(probe1.ref) ~> new Choice(state => state == 1, new Step(probe2.ref) ~> new Step(probe3.ref) , new Step(probe4.ref)) ~> new Step(endProbe.ref)

    process.onComplete { case (context, state) =>
      completeHook.ref ! s"DONE-$state"
    }
  }
}


class PersistentProcessTest extends TestKit(ActorSystem("ProcessStepTest"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {
  import PersistentProcessTest._

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val probe1 = TestProbe()
  val probe2 = TestProbe()
  val probe3 = TestProbe()
  val probe4 = TestProbe()
  val endProbe = TestProbe()
  val completeHookProbe = TestProbe()

  def assertStateIs[State](process: ActorRef, state: State) = {
    process ! Process.GetState
    expectMsg(state)
  }
  def stop(actor: ActorRef) {
    watch(actor)
    system.stop(actor)
    expectTerminated(actor)
  }

  "PersistentProcess" should {
    "persist state" in {
      val process = system.actorOf(Props(new PersistentProcess1(probe1, probe2, probe3, probe4, endProbe, completeHookProbe)), "persisted-process-test1")
      assertStateIs(process, 0)
      process ! Start
      probe1.expectMsg(Command(0))
      probe1.reply(Response)
      eventually {
        assertStateIs(process, 1)
      }
      probe2.expectMsg(Command(1))
      stop(process)
      val newProcess = system.actorOf(Props(new PersistentProcess1(probe1, probe2, probe3, probe4, endProbe, completeHookProbe)), "persisted-process-test1")
      assertStateIs(newProcess, 1)
      probe2.expectMsg(Command(1))
      probe2.reply(Response)
      eventually {
        assertStateIs(newProcess, 2)
      }
      probe3.expectMsg(Command(2))
      probe3.reply(Response)
      eventually {
        assertStateIs(newProcess, 3)
      }
      endProbe.expectMsg(Command(3))
      probe3.reply(Response)
      probe4.expectNoMsg(250 millis)
      completeHookProbe.expectMsg("DONE-4")
    }
  }

}
