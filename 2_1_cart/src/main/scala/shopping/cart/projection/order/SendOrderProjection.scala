package shopping.cart.projection.order

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ ProjectionBehavior, ProjectionId }
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.AtLeastOnceProjection
import shopping.cart.es.{ ShoppingCart, ShoppingCartContext }
import shopping.cart.repository.ScalikeJdbcSession
import shopping.order.proto.ShoppingOrderService

object SendOrderProjection {

  def init(orderService: ShoppingOrderService)(implicit context: ShoppingCartContext): Unit =
    ShardedDaemonProcess(context.system).init(
      name = "SendOrderProjection",
      context.cluster.size,
      index => ProjectionBehavior(newProjection(orderService, index)),
      ShardedDaemonProcessSettings(context.system),
      Some(ProjectionBehavior.Stop)
    )

  private def newProjection(orderService: ShoppingOrderService, index: Int)(
      implicit context: ShoppingCartContext
  ): AtLeastOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    implicit val system: ActorSystem[_] = context.system
    val tag                             = context.getTag(index)

    JdbcProjection.atLeastOnceAsync(
      projectionId = ProjectionId("SendOrderProjection", tag),
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new SendOrderProjectionHandler(orderService),
      sessionFactory = () => new ScalikeJdbcSession()
    )
  }
}
