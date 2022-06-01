package shopping.cart.projection.order

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import akka.util.Timeout
import org.slf4j.LoggerFactory
import shopping.cart.es.ShoppingCart
import shopping.order.proto.Item
import shopping.order.proto.OrderRequest
import shopping.order.proto.ShoppingOrderService

class SendOrderProjectionHandler(
    system: ActorSystem[_],
    orderService: ShoppingOrderService
) extends Handler[EventEnvelope[ShoppingCart.Event]] {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val ec: ExecutionContext = system.executionContext
  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def process(envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] =
    envelope.event match {
      case checkout: ShoppingCart.CheckedOut =>
        sendOrder(checkout)

      case _ =>
        // this projection is only interested in CheckedOut events
        Future.successful(Done)
    }

  private def sendOrder(checkout: ShoppingCart.CheckedOut): Future[Done] = {
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, checkout.cartId)

    entityRef
      .ask(ShoppingCart.Get)
      .flatMap { cart =>
        val items =
          cart.items.iterator.map { case (itemId, quantity) => Item(itemId, quantity) }.toList
        log.info("Sending order of {} items for cart {}.", items.size, checkout.cartId)

        orderService.order(OrderRequest(checkout.cartId, items)).map(_ => Done)
      }
  }
}
