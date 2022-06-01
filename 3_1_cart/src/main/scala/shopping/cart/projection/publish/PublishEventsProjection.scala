package shopping.cart.projection.publish

import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ AtLeastOnceProjection, SourceProvider }
import akka.projection.{ ProjectionBehavior, ProjectionId }
import org.apache.kafka.common.serialization.{ ByteArraySerializer, StringSerializer }
import shopping.cart.ShoppingCartTags
import shopping.cart.es.ShoppingCart
import shopping.cart.repository.ScalikeJdbcSession

object PublishEventsProjection {

  def init(system: ActorSystem[_]): Unit = {
    val topic = system.settings.config.getString("shopping-cart-service.kafka.topic")

    ShardedDaemonProcess(system).init(
      name = "PublishEventsProjection",
      ShoppingCartTags.size,
      index => ProjectionBehavior(newProjection(system, topic, ShoppingCartTags.get(index))),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def newProjection(
      system: ActorSystem[_],
      topic: String,
      tag: String,
  ): AtLeastOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val producer = newProducer(system)

    JdbcProjection.atLeastOnceAsync(
      ProjectionId("PublishEventsProjection", tag),
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new PublishEventsProjectionHandler(system, topic, producer),
      sessionFactory = () => new ScalikeJdbcSession()
    )(system)
  }

  private def newProducer(system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer     = SendProducer(producerSettings)(system)
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "close-sendProducer") {
      () => sendProducer.close()
    }
    sendProducer
  }
}
