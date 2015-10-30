package processframework

import akka.actor.ActorContext
import akka.testkit.TestProbe

case class NoState()

class EmptyStepSpec extends BaseSpec with ProcessStepTestSupport[NoState, EmptyStep[NoState]] {

  def createTestProbe(): TestProbe = TestProbe()

  def createProcessStep(executeProbe: TestProbe)(implicit context: ActorContext): EmptyStep[NoState] = EmptyStep[NoState]()

  "EmptyStep" should {
    "be completed directly" in {
      val emptyStep = processStep()
      emptyStep.isCompleted shouldBe true
    }
    "be executable" in {
      val emptyStep = processStep()
      emptyStep.execute()
    }
    "give a MatchError when receiving a command" in {
      val emptyStep = processStep()
      intercept[MatchError] {
        emptyStep.receiveCommand(ProcessStepTestSupport.ACommand)
      }
      intercept[MatchError] {
        emptyStep.handleReceiveCommand(ProcessStepTestSupport.ACommand)
      }
    }
    "give a MatchError when updating state for an event" in {
      val emptyStep = processStep()
      intercept[MatchError] {
        emptyStep.updateState(ProcessStepTestSupport.AnEvent)
      }
      intercept[MatchError] {
        emptyStep.handleUpdateState(ProcessStepTestSupport.AnEvent)
      }
    }
  }
}
