package processframework

import akka.actor.{ ActorContext, ActorRef }
import akka.testkit.{ TestActor, TestProbe }

object ProcessStepTest {
  case object Completed extends Process.Event
  case object Response
  case class Command(state: Int)

  def testStep(executeProbe: ActorRef)(implicit _actorContext: ActorContext) = new ProcessStep[Int] {
    implicit def context: ActorContext = _actorContext
    def execute()(implicit process: ActorRef) = { state ⇒
      executeProbe ! Command(state)
    }
    def receiveCommand = {
      case Response ⇒
        Completed
    }
    def updateState = {
      case Completed ⇒ { state ⇒
        markDone(state + 1)
      }
    }
  }
}

class ProcessStepTest extends BaseSpec with ProcessStepTestSupport[Int, ProcessStep[Int]] {
  import ProcessStepTest._

  def processProbeWithState[S](state: S): TestProbe = {
    val processProbe = TestProbe()
    processProbe.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot =
        msg match {
          case Process.GetState ⇒
            sender ! state; TestActor.KeepRunning
          case Completed ⇒ testActor ! Completed; TestActor.KeepRunning
        }
    })
    processProbe
  }

  def createTestProbe(): TestProbe = {
    val serviceMockProbe = TestProbe()
    serviceMockProbe.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        msg match {
          case cmd: Command ⇒
            sender ! Response
            TestActor.NoAutoPilot
        }
      }
    })
    serviceMockProbe
  }

  def createProcessStep(executeProbe: TestProbe)(implicit context: ActorContext) = testStep(executeProbe.ref)

  "A ProcessStep" should {
    "handle happy flow" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val step = processStep()

      // Execute the logic (do call)
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])

      // When response is received by process, it will send this to the steps, so they can handle it
      val event = expectMsg(Completed)
      step.isCompleted should not be true

      // The event can be used to retrieve an updateState function
      val updateStateFunction = step.handleUpdateState(event)
      step.isCompleted should not be true
      updateStateFunction(646) should be(647)
      step.isCompleted shouldBe true
    }
    "run the step logic (which does not complete the step)" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val step = processStep()

      // WHEN
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])

      // THEN
      testProbe.expectMsg(Command(432))
      step.isCompleted shouldBe false
    }
    "complete returns an updateState function, which completes the step" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val step = processStep()

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
      val step = processStep()

      // WHEN
      step.handleUpdateState(Completed)(3)

      // AND THEN
      intercept[MatchError] {
        step.handleUpdateState(Completed)
      }
    }
  }
}
