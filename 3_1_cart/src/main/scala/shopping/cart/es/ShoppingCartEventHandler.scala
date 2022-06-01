package shopping.cart.es

import ShoppingCart._

class ShoppingCartEventHandler {

  def handleEvent(state: State, event: Event): State =
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
