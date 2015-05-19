package processframework

import java.lang

import akka.actor._
import Process.AbortEvent
import akka.persistence.PersistentActor
import scala.concurrent.duration._
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }

import org.scalatest._
import org.scalatest.concurrent.Eventually

import scala.reflect._
import scala.util.Success

object PersistentProcessTest {
  case object Start
  case class Response(probe: ActorRef)
  case class Command(i: PersistentProcess1.State)
  case class Completed(probe: String) extends Process.Event
  class Step(probe: ActorRef)(implicit val context: ActorContext) extends ProcessStep[PersistentProcess1.State] {
    def execute()(implicit process: akka.actor.ActorRef): Execution = { state ⇒
      println(s"${probe.path.name}: exec")
      probe ! Command(state)
    }
    def receiveCommand: CommandToEvent = {
      case Response(`probe`) ⇒
        println(s"${probe.path.name}: complete")
        Completed(probe.path.name)
    }
    def updateState: UpdateFunction = {
      case Completed(p) if p == probe.path.name ⇒ { state ⇒
        markDone()
        val newState = state.copy(probeCalled = probe :: state.probeCalled)
        println(s"${probe.path.name}: newState: ${newState.probeCalled.map(_.path.name)}")
        newState
      }
    }
    override def toString: String = s"Step: ${probe.path.name}"

  }

  class InitStep()(implicit val context: ActorContext) extends ProcessStep[PersistentProcess1.State] {
    def execute()(implicit process: akka.actor.ActorRef): Execution = { state ⇒
    }
    def receiveCommand: PartialFunction[Any, Process.Event] = {
      case Start ⇒
        println("complete init")
        Completed("init")
    }

    def updateState: UpdateFunction = {
      case Completed(_) ⇒ { state ⇒
        markDone()
        println("init completed")
        state
      }
    }

    override def toString: String = "Init"
  }

  object PersistentProcess1 {
    case class State(probeCalled: List[ActorRef] = Nil)
  }
  case object Aborted extends AbortEvent
  class PersistentProcess1(probe1: TestProbe, probe2: TestProbe, probe3: TestProbe, probe4: TestProbe, probe5: TestProbe, endProbe: TestProbe, completeHook: TestProbe)(implicit val commandClassTag: ClassTag[Int], implicit val eventClassTag: ClassTag[Boolean]) extends AbortablePersistentProcess[PersistentProcess1.State] {
    import context.dispatcher
    val persistenceId = "PersistentProcess1"

    var state = PersistentProcess1.State()
    val process = new InitStep() ~>
      Par(If[PersistentProcess1.State](_ ⇒ true)(new Step(probe1.ref)), new Step(probe2.ref)) ~>
      new Choice(state ⇒ state.probeCalled.contains(probe1.ref),
        new Step(probe3.ref) ~> new Step(probe4.ref),
        new Step(probe5.ref)) ~>
      new Step(endProbe.ref)

    process.onComplete {
      case (c, s) ⇒
        completeHook.ref ! s"DONE-${s.probeCalled.size}"
    }

    override def createAbortEvent(): AbortEvent = Aborted
  }
}

class PersistentProcessTest extends BaseSpec {
  import PersistentProcessTest._

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val probe1 = TestProbe()
  val probe2 = TestProbe()
  val probe3 = TestProbe()
  val probe4 = TestProbe()
  val probe5 = TestProbe()
  val endProbe = TestProbe()
  val completeHookProbe = TestProbe()

  val probes =
    s"""|probe1: ${probe1.ref.path.name}
       |probe2: ${probe2.ref.path.name}
       |probe3: ${probe3.ref.path.name}
       |probe4: ${probe4.ref.path.name}
       |probe5: ${probe5.ref.path.name}
       |endPre: ${endProbe.ref.path.name}
       |hook  : ${completeHookProbe.ref.path.name}
       |\n""".stripMargin

  println(probes)

  def assertStateContains[State](process: ActorRef, probe: ActorRef) = {
    process ! processframework.Process.GetState
    val msg = expectMsgType[PersistentProcess1.State]
    msg.probeCalled should contain(probe)
  }
  def stop(actor: ActorRef) {
    watch(actor)
    system.stop(actor)
    expectTerminated(actor)
  }

  implicit class TestProbeForStep(testProbe: TestProbe) {
    def expect(process: ActorRef): Unit = {
      val msg = testProbe.expectMsgType[PersistentProcessTest.Command]
      println(s"${testProbe.ref.path.name}: ${msg.i.probeCalled.map(_.path.name)}")
      testProbe.reply(Response(testProbe.ref))
      eventually {
        assertStateContains(process, testProbe.ref)
      }
    }
  }

  "PersistentProcess" should {
    "persist state" in {
      val process = system.actorOf(Props(new PersistentProcess1(probe1, probe2, probe3, probe4, probe5, endProbe, completeHookProbe)), "persisted-process-test1")
      //assertStateIs(process, 0)
      process ! Start
      probe1.expect(process)
      probe2.expect(process)
      probe3.expectMsgPF(3 second) {
        case _: Command ⇒ println("Going to stop the process now")
      }
      stop(process)
      val newProcess = system.actorOf(Props(new PersistentProcess1(probe1, probe2, probe3, probe4, probe5, endProbe, completeHookProbe)), "persisted-process-test1")
      //assertStateIs(newProcess, 1)
      probe3.expect(newProcess)
      probe4.expect(newProcess)
      endProbe.expectMsgPF(1 second) {
        case _: Command ⇒
      }
      endProbe.reply(Response(endProbe.ref))
      probe5.expectNoMsg(250 millis)
      completeHookProbe.expectMsg("DONE-5")
    }
    "handle unhandled recoveryEvents" in {
      class PersistentProcess1(probe1: TestProbe, dropHook: TestProbe, completeHook: TestProbe)(implicit val commandClassTag: ClassTag[Int], implicit val eventClassTag: ClassTag[Boolean]) extends AbortablePersistentProcess[PersistentProcess1.State] {
        import context.dispatcher
        val persistenceId = "PersistentProcess3"

        var state = PersistentProcess1.State()
        val process = new InitStep() ~> new Step(probe1.ref)

        process.onComplete {
          case (c, s) ⇒
            completeHook.ref ! s"DONE-${s.probeCalled.size}"
        }
        override def createAbortEvent(): AbortEvent = Aborted

        override def unhandledRecoveryEvent: PartialFunction[Process.Event, Unit] = {
          case Completed(x) ⇒
            dropHook.ref ! s"Dropped: $x"
        }
      }
      case object Written
      class JustWrite extends PersistentActor {
        override def persistenceId: String = "PersistentProcess3"
        override def receiveRecover: Receive = Actor.emptyBehavior

        override def receiveCommand: Receive = {
          case txt: String ⇒
            persist(Completed(txt)) { evt ⇒ sender() ! Success(()) }
        }
      }

      val writeData = system.actorOf(Props(new JustWrite))
      writeData ! "One"
      expectMsg(Success())
      writeData ! "Two"
      expectMsg(Success())
      watch(writeData)
      writeData ! PoisonPill
      expectTerminated(writeData)

      val probe1 = TestProbe()
      val dropHook = TestProbe()
      val completeHookProbe = TestProbe()

      val process = system.actorOf(Props(new PersistentProcess1(probe1, dropHook, completeHookProbe)))
      dropHook.expectMsg("Dropped: Two")
      probe1.expect(process)
    }
  }
}
