package shopping.cart.es

import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ EventSourcedBehavior, RetentionCriteria }
import shopping.cart.es.ShoppingCart.{ Command, Event }

import scala.concurrent.duration.DurationInt

/**
 * This is an event sourced actor (`EventSourcedBehavior`). An entity managed by Cluster Sharding.
 */
object ShoppingCartActor {

  private val commandHandler = new ShoppingCartCommandHandler()
  private val eventHandler   = new ShoppingCartState.EventHandler()

  def apply(cartId: String): Behavior[Command] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, ShoppingCartState](
        persistenceId = PersistenceId(ShoppingCartCluster.entityName, cartId),
        emptyState = ShoppingCartState.empty,
        commandHandler = (state, command) => commandHandler.handleCommand(cartId, state, command),
        eventHandler = eventHandler.handleEvent
      )
      .withTagger(_ => Set(ShoppingCart.Tags.forText(cartId)))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
}
