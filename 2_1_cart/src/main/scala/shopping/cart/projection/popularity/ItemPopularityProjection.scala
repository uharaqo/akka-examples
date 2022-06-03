package shopping.cart.projection.popularity

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.projection.{ ProjectionBehavior, ProjectionId }
import shopping.cart.es.{ ShoppingCart, ShoppingCartContext }
import shopping.cart.repository.{ ItemPopularityRepository, ScalikeJdbcSession }

object ItemPopularityProjection {

  def init(repository: ItemPopularityRepository)(implicit context: ShoppingCartContext): Unit =
    ShardedDaemonProcess(context.system).init(
      name = "ItemPopularityProjection",
      numberOfInstances = context.cluster.size,
      behaviorFactory = index => ProjectionBehavior(newProjection(repository, index)),
      settings = ShardedDaemonProcessSettings(context.system),
      stopMessage = Some(ProjectionBehavior.Stop)
    )

  private def newProjection(repository: ItemPopularityRepository, index: Int)(
      implicit context: ShoppingCartContext
  ): ExactlyOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {

    implicit val system: ActorSystem[_] = context.system
    val tag                             = context.getTag(index)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("ItemPopularityProjection", tag),
      sourceProvider = EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new ItemPopularityProjectionHandler(tag, repository),
      sessionFactory = () => new ScalikeJdbcSession()
    )
  }
}
