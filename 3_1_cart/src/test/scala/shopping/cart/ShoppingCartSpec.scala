package shopping.cart

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import shopping.cart.es.ShoppingCart._
import shopping.cart.es.{ ShoppingCartActor, ShoppingCartCluster, ShoppingCartContext, ShoppingCartState }

object ShoppingCartSpec {
  val config = ConfigFactory
    .parseString("""
      akka.actor.serialization-bindings {
        "shopping.cart.serialization.CborSerializable" = jackson-cbor
      }
      """)
    .withFallback(EventSourcedBehaviorTestKit.config)
}

class ShoppingCartSpec
    extends ScalaTestWithActorTestKit(ShoppingCartSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val cartId = "testCart"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, ShoppingCartState](
      system,
      ShoppingCartActor(cartId)(new ShoppingCartContext(system, ShoppingCartCluster.disabled()))
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Shopping Cart" should {
    "add item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](replyTo => AddItem("foo", 42, replyTo))
      result1.reply should ===(StatusReply.Success(Summary(Map("foo" -> 42), checkedOut = false)))
      result1.event should ===(ItemAdded(cartId, "foo", 42))
    }

    "reject already added item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)
      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 13, _))
      result2.reply.isError should ===(true)
    }

    "remove item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)
      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](RemoveItem("foo", _))
      result2.reply should ===(StatusReply.Success(Summary(Map.empty, checkedOut = false)))
      result2.event should ===(ItemRemoved(cartId, "foo", 42))
    }

    "adjust quantity" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)
      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AdjustItemQuantity("foo", 43, _))
      result2.reply should ===(StatusReply.Success(Summary(Map("foo" -> 43), checkedOut = false)))
      result2.event should ===(ItemQuantityAdjusted(cartId, "foo", 43, 42))
    }

    "checkout" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](Checkout)
      result2.reply should ===(StatusReply.Success(Summary(Map("foo" -> 42), checkedOut = true)))
      result2.event.asInstanceOf[CheckedOut].cartId should ===(cartId)

      val result3 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("bar", 13, _))
      result3.reply.isError should ===(true)
    }

    "get" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 = eventSourcedTestKit.runCommand[Summary](Get)
      result2.reply should ===(Summary(Map("foo" -> 42), checkedOut = false))
    }

    "keep its state" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](AddItem("foo", 42, _))
      result1.reply should ===(StatusReply.Success(Summary(Map("foo" -> 42), checkedOut = false)))

      eventSourcedTestKit.restart()

      val result2 = eventSourcedTestKit.runCommand[Summary](Get(_))
      result2.reply should ===(Summary(Map("foo" -> 42), checkedOut = false))
    }
  }
}
