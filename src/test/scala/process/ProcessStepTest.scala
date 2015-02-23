package process

import akka.actor.{ ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import matchers.ShouldMatchers._

object ProcessStepTest {
  case object Complete
  def testStep(executeProbe: ActorRef) = new ProcessStep[Int] {
    def execute()(implicit process: ActorRef): Int => Unit = { state =>
      executeProbe ! state
    }
    def complete: PartialFunction[Any, Int => Int] = {
      case Complete => { state =>
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
          case Process.GetState ⇒ sender ! state; TestActor.NoAutoPilot
        }
    })
    processProbe
  }

  "A ProcessStep" should {
    "run the step logic (which does not complete the step)" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val executeProbe = TestProbe()
      val step = testStep(executeProbe.ref)

      // WHEN
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])

      // THEN
      executeProbe.expectMsg(432)
      step.isCompleted shouldBe false
    }
    "complete returns an updateState function, which completes the step" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val executeProbe = TestProbe()
      val step = testStep(executeProbe.ref)

      // WHEN
      val updateStateFunction = step.doComplete(Complete)

      // THEN
      step.isCompleted shouldBe false

      // WHEN RUN
      val result = updateStateFunction(7)
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
      step.doComplete(Complete)(3)

      // AND THEN
      intercept[MatchError] {
        step.doComplete(Complete)
      }
    }
  }

}
