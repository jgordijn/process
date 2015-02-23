package process

import akka.actor.{ ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import matchers.ShouldMatchers._

object ProcessStepTest {
  case object Completed extends Process.Event
  case object Response
  case object Command
  def testStep(executeProbe: ActorRef) = new ProcessStep[Int] {
    def execute()(implicit process: ActorRef): Int => Unit = { state =>
      executeProbe ! Command
    }
    def receiveCommand: PartialFunction[Any, Process.Event] = {
      case Response => Completed
    }
    def updateState = {
      case Completed => { state =>
        markDone()
        state + 1
      }
    }
  }
}
class ProcessStepTest extends TestKit(ActorSystem("ProcessStepTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  import ProcessStepTest._

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  def processProbeWithState[S](state: S): TestProbe = {
    val processProbe = TestProbe()
    processProbe.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot =
        msg match {
          case Process.GetState ⇒ sender ! state; TestActor.KeepRunning
          case Response => testActor ! Response; TestActor.KeepRunning
        }
    })
    processProbe
  }
  def createServiceMockProbe: TestProbe = {
    val serviceMockProbe = TestProbe()
    serviceMockProbe.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        msg match {
          case Command => sender ! Response; TestActor.NoAutoPilot
        }
      }
    })
    serviceMockProbe
  }

  "A ProcessStep" should {
    "handle happy flow" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val serviceMockProbe = createServiceMockProbe
      val step = testStep(serviceMockProbe.ref)

      // Execute the logic (do call)
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])
      expectMsg(Response)

      // When response is received by process, it will send this to the steps, so they can handle it
      val event = step.handleReceiveCommand(Response)
      event should be (Completed)
      step.isCompleted should not be(true)

      // The event can be used to retrieve an updateState function
      val updateStateFunction = step.handleUpdateState(event)
      step.isCompleted should not be(true)
      updateStateFunction(646) should be (647)
      step.isCompleted should be(true)

    }
    "run the step logic (which does not complete the step)" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val executeProbe = TestProbe()
      val step = testStep(executeProbe.ref)

      // WHEN
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])

      // THEN
      executeProbe.expectMsg(Command)
      step.isCompleted shouldBe false
    }
    "complete returns an updateState function, which completes the step" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val serviceMockProbe = createServiceMockProbe
      val step = testStep(serviceMockProbe.ref)

      // WHEN
      val event = step.receiveCommand(Response)

      // THEN
      step.isCompleted shouldBe false

      // WHEN RUN
      val result = step.handleUpdateState(event)(7)
      // THEN
      result should be(8)
      step.isCompleted shouldBe true
    }
    "can only be completed once" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val executeProbe = TestProbe()
      val step = testStep(executeProbe.ref)

      // WHEN
      step.handleUpdateState(Completed)(3)

      // AND THEN
      intercept[MatchError] {
        step.handleUpdateState(Completed)
      }
    }
  }

}
