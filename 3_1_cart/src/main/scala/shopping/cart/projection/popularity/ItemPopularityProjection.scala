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
import shopping.cart.es.ShoppingCart
import shopping.cart.repository.{ ItemPopularityRepository, ScalikeJdbcSession }

object ItemPopularityProjection {

  def init(system: ActorSystem[_], repository: ItemPopularityRepository): Unit =
    ShardedDaemonProcess(system).init(
      name = "ItemPopularityProjection",
      numberOfInstances = ShoppingCart.Tags.size,
      behaviorFactory = index => ProjectionBehavior(newProjection(system, repository, index)),
      settings = ShardedDaemonProcessSettings(system),
      stopMessage = Some(ProjectionBehavior.Stop)
    )

  private def newProjection(
      system: ActorSystem[_],
      repository: ItemPopularityRepository,
      index: Int
  ): ExactlyOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val tag = ShoppingCart.Tags.get(index)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("ItemPopularityProjection", tag),
      sourceProvider = EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new ItemPopularityProjectionHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession()
    )(system)
  }
}
