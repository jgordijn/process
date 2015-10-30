package processframework

import akka.actor.{ ActorLogging, ActorRef, Props, ActorContext }

class CounterPersistentProcessSpec extends BaseSpec {
  import CounterPersistentProcess._

  var initialCount: Int = 5
  var counterPersistentProcess: ActorRef = null

  override protected def beforeEach(): Unit = {
    counterPersistentProcess = system.actorOf(Props(new CounterPersistentProcess(initialCount)(testActor)))
    initialCount += 10
  }

  "Counter persistent process" should {
    "increment only once even though multiple commands are fired" in {
      counterPersistentProcess ! Increment
      expectMsg[Int](initialCount + 1)

      // superfluous amount of Increment commands
      counterPersistentProcess ! Increment
      counterPersistentProcess ! Increment

      counterPersistentProcess ! Process.GetState
      expectMsg[Int](initialCount + 1)
    }
    "increment only once even though multiple events are simulated" in {
      counterPersistentProcess ! CountIncremented
      expectMsg[Int](initialCount + 1)

      // superfluous amount of CountIncremented events
      counterPersistentProcess ! CountIncremented
      counterPersistentProcess ! CountIncremented

      counterPersistentProcess ! Process.GetState
      expectMsg[Int](initialCount + 1)
    }
    "increment & decrement only once when multiple commands and events are mixed" in {
      counterPersistentProcess ! Increment
      counterPersistentProcess ! CountIncremented

      counterPersistentProcess ! CountDecremented
      counterPersistentProcess ! Decrement

      counterPersistentProcess ! Process.GetState
      expectMsg[Int](initialCount)
    }
  }
}

object CounterPersistentProcess {
  trait Command
  case object Increment extends Command
  case object Decrement extends Command

  case object CountIncremented extends Process.Event
  case object CountDecremented extends Process.Event
}

class CounterPersistentProcess(val initialCount: Int)(testActor: ActorRef) extends PersistentProcess[Int] with ActorLogging {
  import context.dispatcher

  val incrementStep = new IncrementProcessStep()
  val decrementStep = new DecrementProcessStep()

  val process = Par(incrementStep, decrementStep)

  incrementStep onComplete { _ ⇒
    testActor ! state
  }

  val persistenceId: String = "counter-persistent-process"
  var state: Int = initialCount
}

class IncrementProcessStep()(implicit val context: ActorContext) extends ProcessStep[Int] {
  def execute()(implicit process: akka.actor.ActorRef): Execution = { _ ⇒ }

  def receiveCommand: PartialFunction[Any, Process.Event] = {
    case CounterPersistentProcess.Increment ⇒
      CounterPersistentProcess.CountIncremented
  }

  def updateState: UpdateFunction = {
    case CounterPersistentProcess.CountIncremented ⇒ { state ⇒
      markDone(state + 1)
    }
  }
}

class DecrementProcessStep()(implicit val context: ActorContext) extends ProcessStep[Int] {
  def execute()(implicit process: akka.actor.ActorRef): Execution = { _ ⇒ }

  def receiveCommand: PartialFunction[Any, Process.Event] = {
    case CounterPersistentProcess.Decrement ⇒ CounterPersistentProcess.CountDecremented
  }

  def updateState: UpdateFunction = {
    case CounterPersistentProcess.CountDecremented ⇒ { state ⇒
      markDone()
      state - 1
    }
  }
}
