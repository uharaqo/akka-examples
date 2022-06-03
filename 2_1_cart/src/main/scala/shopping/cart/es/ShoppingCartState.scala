package shopping.cart.es

import ShoppingCart._
import shopping.cart.serialization.CborSerializable

import java.time.Instant

/**
 * State which holds the current shopping cart items and whether it's checked out.
 */
object ShoppingCartState {
  val empty: ShoppingCartState = ShoppingCartState(items = Map.empty, checkoutDate = None)

  /**
   * The event handler updates the current state based on the event.
   * This is done when the event is first created, and when the entity is loaded from the database - each event will be replayed to recreate the state of the entity.
   */
  class EventHandler {

    def handleEvent(state: ShoppingCartState, event: Event): ShoppingCartState =
      event match {
        case ItemAdded(_, itemId, quantity) =>
          state.updateItem(itemId, quantity)

        case CheckedOut(_, eventTime) =>
          state.checkout(eventTime)

        case ItemRemoved(_, itemId, _) =>
          state.removeItem(itemId)

        case ItemQuantityAdjusted(_, itemId, quantity, _) =>
          state.updateItem(itemId, quantity)
      }
  }
}

final case class ShoppingCartState(items: Map[String, Int], checkoutDate: Option[Instant]) extends CborSerializable {

  def isCheckedOut: Boolean =
    checkoutDate.isDefined

  def hasItem(itemId: String): Boolean =
    items.contains(itemId)

  def isEmpty: Boolean =
    items.isEmpty

  def toSummary: Summary =
    Summary(items, isCheckedOut)

  def updateItem(itemId: String, quantity: Int): ShoppingCartState =
    quantity match {
      case 0 => copy(items = items - itemId)
      case _ => copy(items = items + (itemId -> quantity))
    }

  def removeItem(itemId: String): ShoppingCartState =
    copy(items = items - itemId)

  def checkout(now: Instant): ShoppingCartState =
    copy(checkoutDate = Some(now))
}
