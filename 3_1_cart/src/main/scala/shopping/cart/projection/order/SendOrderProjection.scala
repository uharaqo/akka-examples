package shopping.cart.projection.order

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.AtLeastOnceProjection
import shopping.cart.es.ShoppingCart
import shopping.cart.repository.ScalikeJdbcSession
import shopping.order.proto.ShoppingOrderService

object SendOrderProjection {

  def init(system: ActorSystem[_], orderService: ShoppingOrderService): Unit =
    ShardedDaemonProcess(system).init(
      name = "SendOrderProjection",
      ShoppingCart.Tags.size,
      index => ProjectionBehavior(newProjection(system, orderService, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )

  private def newProjection(
      system: ActorSystem[_],
      orderService: ShoppingOrderService,
      index: Int
  ): AtLeastOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val tag = ShoppingCart.Tags.get(index)

    JdbcProjection.atLeastOnceAsync(
      projectionId = ProjectionId("SendOrderProjection", tag),
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new SendOrderProjectionHandler(system, orderService),
      sessionFactory = () => new ScalikeJdbcSession()
    )(system)
  }
}
