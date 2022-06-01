package shopping.cart.es

import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{ Effect, ReplyEffect }
import ShoppingCart._

import java.time.Instant

class ShoppingCartCommandHandler {

  def handleCommand(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    if (state.isCheckedOut)
      checkedOutShoppingCart(cartId, state, command)
    else
      openShoppingCart(cartId, state, command)

  private def openShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)

      case AddItem(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(StatusReply.Error(s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect.persist(ItemAdded(cartId, itemId, quantity)).thenReply(replyTo) { updatedCart =>
            StatusReply.Success(updatedCart.toSummary)
          }

      case Checkout(replyTo) =>
        if (state.isEmpty)
          Effect.reply(replyTo)(StatusReply.Error("Cannot checkout an empty shopping cart"))
        else
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))

      case RemoveItem(itemId, replyTo) =>
        if (state.hasItem(itemId))
          Effect
            .persist(ItemRemoved(cartId, itemId, state.items(itemId)))
            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(
            StatusReply.Success(state.toSummary)
          ) // removing an item is idempotent

      case AdjustItemQuantity(itemId, quantity, replyTo) =>
        if (quantity <= 0)
          Effect.reply(replyTo)(StatusReply.Error("Quantity must be greater than zero"))
        else if (state.hasItem(itemId))
          Effect
            .persist(ItemQuantityAdjusted(cartId, itemId, quantity, state.items(itemId)))
            .thenReply(replyTo)(updatedCart => StatusReply.Success(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(
            StatusReply.Error(s"Cannot adjust quantity for item '$itemId'. Item not present on cart")
          )
    }

  private def checkedOutShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)

      case cmd: AddItem =>
        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't add an item to an already checked out shopping cart"))

      case cmd: Checkout =>
        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't checkout already checked out shopping cart"))

      case cmd: RemoveItem =>
        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't remove an item from an already checked out shopping cart"))

      case cmd: AdjustItemQuantity =>
        Effect.reply(cmd.replyTo)(StatusReply.Error("Can't adjust item on an already checked out shopping cart"))
    }
}
