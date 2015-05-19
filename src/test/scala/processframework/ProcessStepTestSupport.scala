package processframework

import akka.pattern.ask
import akka.actor.{ ActorRef, ActorContext, Actor, Props }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.reflect.ClassTag

import akka.testkit.{ TestProbe, TestKit }
import org.scalatest.BeforeAndAfterEach

object ProcessStepTestSupport {
  case object GetStep
  case object ACommand
  case object AnEvent extends Process.Event
}

trait ProcessStepTestSupport[S, PS <: ProcessStep[S]] { this: TestKit with BeforeAndAfterEach ⇒
  implicit val timeout: Timeout = 1 second

  var testProbe: TestProbe = null
  var processActor: ActorRef = null

  override protected def beforeEach(): Unit = {
    testProbe = createTestProbe()
    processActor = createProcessActor()
  }

  def createTestProbe(): TestProbe
  def createProcessStep(executeProbe: TestProbe)(implicit context: ActorContext): PS

  def createProcessActor() = system.actorOf(Props(new Actor {
    val step = createProcessStep(testProbe)

    def receive = {
      case msg if sender() == step        ⇒ testActor forward msg
      case ProcessStepTestSupport.GetStep ⇒ sender() ! step
      case e: Process.Event               ⇒ testActor ! e
    }
  }))

  def processStep()(implicit classTag: ClassTag[PS]): PS =
    Await.result[PS]((processActor ? ProcessStepTestSupport.GetStep).mapTo[PS], 2 seconds)
}
