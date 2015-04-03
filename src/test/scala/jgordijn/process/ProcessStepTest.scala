package jgordijn.process

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorContext
import akka.actor.Props
import akka.actor.{ ActorRef, ActorSystem }
import akka.testkit.{ ImplicitSender, TestActor, TestKit, TestProbe }

import org.scalatest._
import matchers.ShouldMatchers._
import scala.concurrent.Await

object ProcessStepTest {
  case object Completed extends Process.Event
  case object Response
  case class Command(state: Int)
  def testStep(executeProbe: ActorRef)(implicit _actorContext: ActorContext) = new ProcessStep[Int] {
    implicit def context = _actorContext
    def execute()(implicit process: ActorRef) = { state =>
      executeProbe ! Command(state)
    }
    def receiveCommand = {
      case Response =>
        Completed
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
          case Completed => testActor ! Completed; TestActor.KeepRunning
        }
    })
    processProbe
  }
  def createServiceMockProbe: TestProbe = {
    val serviceMockProbe = TestProbe()
    serviceMockProbe.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = {
        msg match {
          case cmd: Command =>
            sender ! Response
            TestActor.NoAutoPilot
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

      case object GetStep
      val parent = system.actorOf(Props(new Actor {
        val step = testStep(serviceMockProbe.ref)
        def receive = {
          case x if sender() == step =>
            testActor forward x
          case GetStep => sender ! step
          case e: Process.Event => testActor ! e
        }
      }))

      parent ! GetStep
      val step = expectMsgType[ProcessStep[Int]]

      // Execute the logic (do call)
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])
      //Await.result(future, 3 seconds)
      // When response is received by process, it will send this to the steps, so they can handle it
      val event = expectMsg(Completed)
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

      val parent = system.actorOf(Props(new Actor {
        val step = testStep(executeProbe.ref)
        def receive = {
          case x if sender() == step => testActor forward x
          case "GET" => sender ! step
        }
      }))

      parent ! "GET"
      val step = expectMsgType[ProcessStep[Int]]

      // WHEN
      val future = step.run()(processProbe.ref, system.dispatcher, scala.reflect.classTag[Int])

      // THEN
      executeProbe.expectMsg(Command(432))
      step.isCompleted shouldBe false
    }
    "complete returns an updateState function, which completes the step" in {
      // GIVEN
      val processProbe = processProbeWithState(432)
      val serviceMockProbe = createServiceMockProbe
//      val step = testStep(serviceMockProbe.ref)
      val parent = system.actorOf(Props(new Actor {
        val step = testStep(serviceMockProbe.ref)
        def receive = {
          case x if sender() == step => testActor forward x
          case "GET" => sender ! step
        }
      }))

      parent ! "GET"
      val step = expectMsgType[ProcessStep[Int]]


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
      //val step = testStep(executeProbe.ref)
      val parent = system.actorOf(Props(new Actor {
        val step = testStep(executeProbe.ref)
        def receive = {
          case x if sender() == step => testActor forward x
          case "GET" => sender ! step
        }
      }))

      parent ! "GET"
      val step = expectMsgType[ProcessStep[Int]]


      // WHEN
      step.handleUpdateState(Completed)(3)

      // AND THEN
      intercept[MatchError] {
        step.handleUpdateState(Completed)
      }
    }
  }

}
