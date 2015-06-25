# Process

[![Join the chat at https://gitter.im/jgordijn/process](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jgordijn/process?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
A small framework to define long running (persistent)processes within Akka.

## Getting process
If you're using SBT, add the following lines to your build file:

```
resolvers += "processFramework at bintray" at "https://dl.bintray.com/jgordijn/maven/"

libraryDependencies += "processframework" %% "process" % "0.1.15"
```

For Maven and other build tools, you can visit search.maven.org

## Intro
If you tried to write a long running (persistent) process in Akka, you
will find out that this will result in a lot of messages handling in a
single actor. It is not trivial to see what the process flow is and if
there are parallel parts. Primary reason is that different steps are
implemented in a single class. This library tries to make life easier
by doing 2 things:

1. Create a separate class for each step in the process flow
2. Create a process class that describes how the steps are linked
together


## Steps
A process consists of different steps that are performed one after the
other or in parallel. With _Process_ you define every step in its own
class.

```scala
case class DemoState(demoed: Boolean)
class DemoStep(demoService: ActorRef)(implicit val context: ActorContext)
    extends ProcessStep[DemoState] {

  // Code to execute when the step should perform it's task
  def execute()(implicit stepActor: ActorRef): Execution = state => {
    demoService ! Command(state.demoed)
  }

  // This catches the responses from the async execute action.
  // It emits Process.Event to the process.
  def receiveCommand: CommandToEvent = {
    case ReplyFromDemoService =>
      Demoed
  }

  // The updateState function handles events and changes the state. It
  // should mark the step as done when the event is the last event for
  // this step.
  def updateState: UpdateFunction = {
    case Demoed => { state =>
      markDone()
      state.copy(demoed = true)
    }
  }
}
```


## Process
The main goal of the process is to create a flow of steps. It is
possible to chain different steps, so that they are performed in
sequence. It is also possible to parallelize steps.

```scala
class DemoProcess(demoService: ActorRef) extends Process[DemoState] {
  // implicit ExecutionContext is needed
  import context.dispatcher
  var state = DemoState(false)
  val step1 = new DemoStep(demoService)
  // Subflows can be created
  val subflow1 = subStep1 ~> subStep2
  val subflow2 = subStepX ~> subStepY

  // process defines the complete process
  val process = step1 ~> step2 ~> Par(subflow1, subflow2) ~> step3

  def receiveCommand = {
    case CommandToStartProcess =>
      process.run()
  }
}
```

### PersistentProcess
Long running processes should survive restarts. Therefore it should
persist the events and automatically restart when the process is
created. It is almost as easy as changing `Process[S]` to
`PersistentProcess[S]` (where `S` is the type of the state). The only
difference is that you need to specify a `persistenceId`.

```scala
class DemoProcess(demoService: ActorRef) extends PersistentProcess[DemoState] {
  val persistenceId = "demoProcess"
  // implicit ExecutionContext is needed
  import context.dispatcher
  var state = DemoState(false)
  val step1 = new DemoStep(demoService)
  // Subflows can be created
  val subflow1 = subStep1 ~> subStep2
  val subflow2 = subStepX ~> subStepY

  // process defines the complete process
  val process = step1 ~> step2 ~> Par(subflow1, subflow2) ~> step3

  def receiveCommand = {
    case CommandToStartProcess =>
      process.run()
  }
}
```
