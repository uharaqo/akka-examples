package shopping.cart.es

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import shopping.cart.proto
import shopping.cart.serialization.CborSerializable

import java.time.Instant

object ShoppingCart {

  sealed trait Command extends CborSerializable

  /**
   * A command to add an item to the cart.
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class RemoveItem(itemId: String, replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class AdjustItemQuantity(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class Checkout(replyTo: ActorRef[StatusReply[Summary]]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command

  final case class Summary(items: Map[String, Int], checkedOut: Boolean) extends CborSerializable {

    def toProtoCart: proto.Cart =
      proto.Cart(
        items.iterator.map { case (itemId, quantity) => proto.Item(itemId, quantity) }.toSeq,
        checkedOut
      )
  }

  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event

  final case class ItemRemoved(cartId: String, itemId: String, oldQuantity: Int) extends Event

  final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int, oldQuantity: Int)
      extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

  object Tags {

    private val tags = Vector.tabulate(5)(i => s"carts-$i")

    val size: Int = tags.size

    def get(index: Int): String =
      tags(math.abs(index % tags.size))

    def forText(text: String): String =
      get(text.hashCode)
  }
}
