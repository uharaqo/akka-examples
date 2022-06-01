package shopping.cart.es

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, RetentionCriteria }
import shopping.cart.{ proto, ShoppingCartTags }
import shopping.cart.serialization.CborSerializable

import java.time.Instant
import scala.concurrent.duration.DurationInt

/**
 * This is an event sourced actor (`EventSourcedBehavior`). An entity managed by Cluster Sharding.
 *
 * It has a state, [[ShoppingCart.State]], which holds the current shopping cart items
 * and whether it's checked out.
 *
 * You interact with event sourced actors by sending commands to them,
 * see classes implementing [[ShoppingCart.Command]].
 *
 * The command handler validates and translates commands to events, see classes implementing [[ShoppingCart.Event]].
 * It's the events that are persisted by the `EventSourcedBehavior`. The event handler updates the current
 * state based on the event. This is done when the event is first created, and when the entity is
 * loaded from the database - each event will be replayed to recreate the state
 * of the entity.
 */
object ShoppingCart {

  final case class State(items: Map[String, Int], checkoutDate: Option[Instant]) extends CborSerializable {

    def isCheckedOut: Boolean =
      checkoutDate.isDefined

    def hasItem(itemId: String): Boolean =
      items.contains(itemId)

    def isEmpty: Boolean =
      items.isEmpty

    def updateItem(itemId: String, quantity: Int): State =
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }

    def removeItem(itemId: String): State =
      copy(items = items - itemId)

    def checkout(now: Instant): State =
      copy(checkoutDate = Some(now))

    def toSummary: Summary =
      Summary(items, isCheckedOut)
  }

  object State {
    val empty: State = State(items = Map.empty, checkoutDate = None)
  }

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

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  private val commandHandler = new ShoppingCartCommandHandler()
  private val eventHandler   = new ShoppingCartEventHandler()

  def apply(cartId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler = (state, command) => commandHandler.handleCommand(cartId, state, command),
        eventHandler = eventHandler.handleEvent
      )
      .withTagger(_ => Set(ShoppingCartTags.forText(cartId)))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
}
