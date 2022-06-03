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
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.projection.{ ProjectionBehavior, ProjectionId }
import org.apache.kafka.common.serialization.{ ByteArraySerializer, StringSerializer }
import shopping.cart.es.{ ShoppingCart, ShoppingCartContext }
import shopping.cart.repository.ScalikeJdbcSession

object PublishEventsProjection {

  def init()(implicit context: ShoppingCartContext): Unit = {
    val system = context.system
    val topic  = system.settings.config.getString("shopping-cart-service.kafka.topic")

    ShardedDaemonProcess(system).init(
      name = "PublishEventsProjection",
      context.cluster.size,
      index => ProjectionBehavior(newProjection(index, topic)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop)
    )
  }

  private def newProjection(index: Int, topic: String)(
      implicit context: ShoppingCartContext
  ): AtLeastOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {

    implicit val system: ActorSystem[_] = context.system
    val tag                             = context.getTag(index)
    val producer                        = newProducer()

    JdbcProjection.atLeastOnceAsync(
      ProjectionId("PublishEventsProjection", tag),
      EventSourcedProvider.eventsByTag[ShoppingCart.Event](system, JdbcReadJournal.Identifier, tag),
      handler = () => new PublishEventsProjectionHandler(topic, producer),
      sessionFactory = () => new ScalikeJdbcSession()
    )
  }

  private def newProducer()(implicit system: ActorSystem[_]): SendProducer[String, Array[Byte]] = {
    val producerSettings = ProducerSettings(system, new StringSerializer, new ByteArraySerializer)
    val sendProducer     = SendProducer(producerSettings)
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "close-sendProducer") {
      () => sendProducer.close()
    }
    sendProducer
  }
}
