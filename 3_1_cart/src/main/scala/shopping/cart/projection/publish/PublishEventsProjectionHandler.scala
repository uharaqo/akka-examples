package shopping.cart.projection.publish

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.SendProducer
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.google.protobuf.any.{ Any => ScalaPBAny }
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import shopping.cart.es.ShoppingCart
import shopping.cart.proto._

class PublishEventsProjectionHandler(
    topic: String,
    sendProducer: SendProducer[String, Array[Byte]],
)(implicit system: ActorSystem[_])
    extends Handler[EventEnvelope[ShoppingCart.Event]] {

  private val log = LoggerFactory.getLogger(getClass)

  implicit private val ec: ExecutionContext = system.executionContext

  override def process(envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = {
    val event = envelope.event

    // using the cartId as the key and `DefaultPartitioner` will select partition based on the key
    // so that events for same cart always ends up in same partition
    val key   = event.cartId
    val value = serialize(event)

    sendProducer.send(new ProducerRecord(topic, key, value)).map { recordMetadata =>
      log.info("Published event [{}] to topic/partition {}/{}", event, topic, recordMetadata.partition)
      Done
    }
  }

  private def serialize(event: ShoppingCart.Event): Array[Byte] = {
    val protoMessage = event match {
      case ShoppingCart.ItemAdded(cartId, itemId, quantity) =>
        ItemAdded(cartId, itemId, quantity)

      case ShoppingCart.ItemQuantityAdjusted(cartId, itemId, quantity, _) =>
        ItemQuantityAdjusted(cartId, itemId, quantity)

      case ShoppingCart.ItemRemoved(cartId, itemId, _) =>
        ItemRemoved(cartId, itemId)

      case ShoppingCart.CheckedOut(cartId, _) =>
        CheckedOut(cartId)
    }

    // pack in Any so that type information is included for deserialization
    ScalaPBAny.pack(protoMessage, "shopping-cart-service").toByteArray
  }
}
