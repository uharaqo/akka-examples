package shopping.cart.grpc

import akka.actor.typed.{ ActorRef, ActorSystem, DispatcherSelector }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.pattern.StatusReply
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import shopping.cart.es.ShoppingCart
import shopping.cart.proto
import shopping.cart.repository.{ ItemPopularityRepository, ScalikeJdbcSession }

import java.util.concurrent.TimeoutException
import scala.concurrent.{ ExecutionContext, Future }

class ShoppingCartServiceImpl(system: ActorSystem[_], itemPopularityRepository: ItemPopularityRepository)
    extends proto.ShoppingCartService {

  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  // for projection
  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )

  // apis
  override def addItem(in: proto.AddItemRequest): Future[proto.Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.AddItem(in.itemId, in.quantity, _))
    convertError(reply.map(_.toProtoCart))
//    Future.successful(proto.Cart(items = List(proto.Item(in.itemId, in.quantity))))
  }

  override def updateItem(in: proto.UpdateItemRequest): Future[proto.Cart] = {
    logger.info("updateItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)

    def command(replyTo: ActorRef[StatusReply[ShoppingCart.Summary]]) =
      if (in.quantity == 0)
        ShoppingCart.RemoveItem(in.itemId, replyTo)
      else
        ShoppingCart.AdjustItemQuantity(in.itemId, in.quantity, replyTo)

    val reply = entityRef.askWithStatus(command)
    convertError(reply.map(_.toProtoCart))
  }

  override def checkout(in: proto.CheckoutRequest): Future[proto.Cart] = {
    logger.info("checkout {}", in.cartId)
    val entityRef                           = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] = entityRef.askWithStatus(ShoppingCart.Checkout)
    convertError(reply.map(_.toProtoCart))
  }

  override def getCart(in: proto.GetCartRequest): Future[proto.Cart] = {
    logger.info("getCart {}", in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val response =
      entityRef.ask(ShoppingCart.Get).map { cart =>
        if (cart.items.isEmpty)
          throw new GrpcServiceException(Status.NOT_FOUND.withDescription(s"Cart ${in.cartId} not found"))
        else
          cart.toProtoCart
      }
    convertError(response)
  }

  // projection
  override def getItemPopularity(in: proto.GetItemPopularityRequest): Future[proto.GetItemPopularityResponse] =
    Future {
      ScalikeJdbcSession.withSession { session =>
        itemPopularityRepository.getItem(session, in.itemId)
      }
    }(blockingJdbcExecutor).map {
      case Some(count) =>
        proto.GetItemPopularityResponse(in.itemId, count)
      case None =>
        proto.GetItemPopularityResponse(in.itemId)
    }

  // utils
  private def convertError[T](response: Future[T]): Future[T] =
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(new GrpcServiceException(Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
}
