package processframework

import akka.actor.ActorSystem

import org.scalatest.{WordSpecLike, Suite, Matchers, BeforeAndAfterAll}
import org.scalatest.concurrent.Eventually

import akka.testkit.{ImplicitSender, TestProbe, TestKit}

abstract class BaseSpec extends TestKit(ActorSystem(getClass.getSimpleName.stripSuffix("$")))
  with WordSpecLike
  with Suite
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender
  with Eventually
