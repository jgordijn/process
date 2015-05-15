package processframework

import akka.actor.ActorSystem

import org.scalatest._
import org.scalatest.concurrent.Eventually

import akka.testkit.{ ImplicitSender, TestProbe, TestKit }

abstract class BaseSpec extends TestKit(ActorSystem(getClass.getSimpleName.stripSuffix("$")))
    with WordSpecLike
    with Suite
    with Matchers
    with ShouldMatchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ImplicitSender
    with Eventually {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
