package jgordijn.process

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorContext, ActorRef, Props }
import akka.util.Timeout

object Par {
  def apply[S](steps: ProcessStep[S]*)(implicit context: ActorContext): ProcessStep[S] = new Chain[S](EmptyStep(), steps: _*)
}
