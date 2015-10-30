package processframework

import akka.actor.ActorContext
import akka.testkit.TestProbe

class PatternSpec extends BaseSpec with ProcessStepTestSupport[Int, Pattern[Int, Int]] {

  def createTestProbe(): TestProbe = TestProbe()

  private def int(i: Int) = i

  def createProcessStep(executeProbe: TestProbe)(implicit context: ActorContext): Pattern[Int, Int] = new Pattern[Int, Int](
    matchFunction = int,
    nextProcessStep = {
      case 1 ⇒ new IncrementProcessStep()
      case _ ⇒ EmptyStep()
    }
  )

  "Pattern" should {
    "behave like EmptyStep for e.g. state = '0'" in {
      val patternStep = processStep()
      patternStep.isCompleted shouldBe false

      patternStep.execute()(processActor)(0)

      patternStep.isCompleted shouldBe true

      intercept[MatchError] {
        patternStep.receiveCommand(CounterPersistentProcess.Increment)
      }

      patternStep.isCompleted shouldBe true

      intercept[MatchError] {
        patternStep.updateState(CounterPersistentProcess.CountIncremented)
      }
      patternStep.isCompleted shouldBe true
    }
    "behave like IncrementProcessStep for state ='1'" in {
      val state = 1
      val patternStep = processStep()
      patternStep.isCompleted shouldBe false

      patternStep.execute()(processActor)(state)

      patternStep.isCompleted shouldBe false

      val event = patternStep.receiveCommand(CounterPersistentProcess.Increment)
      event shouldBe CounterPersistentProcess.CountIncremented

      patternStep.isCompleted shouldBe false

      val result = patternStep.updateState(event)(state)
      result shouldBe 2

      patternStep.isCompleted shouldBe true
    }
  }
}