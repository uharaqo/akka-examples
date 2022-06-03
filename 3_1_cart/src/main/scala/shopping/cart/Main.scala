package shopping.cart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import shopping.cart.es.{ ShoppingCartCluster, ShoppingCartContext }
import shopping.cart.grpc.{ GrpcServer, ShoppingCartServiceImpl }
import shopping.cart.projection.order.SendOrderProjection
import shopping.cart.projection.popularity.ItemPopularityProjection
import shopping.cart.projection.publish.PublishEventsProjection
import shopping.cart.proto.{ ShoppingCartService, ShoppingCartServiceHandler }
import shopping.cart.repository.{ ItemPopularityRepositoryImpl, ScalikeJdbcSetup }
import shopping.order.proto.{ ShoppingOrderService, ShoppingOrderServiceClient }

import scala.util.control.NonFatal

object Main {

  private val logger = LoggerFactory.getLogger("shopping.cart.Main")

  def main(args: Array[String]): Unit = {
    logger.info("Starting Akka")

    val system =
      ActorSystem[Nothing](Behaviors.empty, "ShoppingCartService")
    try {
      logger.info("Starting OrderServiceClient")
      val orderService = ShoppingOrderServiceClient(newOrderServiceClientSettings(system))(system)
      init(system, orderService)
      logger.info("Started Akka")

    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_], orderService: ShoppingOrderService): Unit = {

    logger.info("Starting AkkaManagement")
    AkkaManagement(system).start()

    logger.info("Starting AkkaCluster")
    ClusterBootstrap(system).start()

    val clusterSize                           = 5
    val cluster                               = ShoppingCartCluster("ShoppingCart", clusterSize)(system)
    implicit val context: ShoppingCartContext = new ShoppingCartContext(system, cluster)

    logger.info("Starting ShoppingCart")
    ClusterSharding(system).init(context.cluster.newShardedEntity())

    logger.info("Starting Projection")
    ScalikeJdbcSetup.init(system)
    val itemPopularityRepository = new ItemPopularityRepositoryImpl()
    ItemPopularityProjection.init(itemPopularityRepository)

    logger.info("Starting Projection PublishEvents")
    PublishEventsProjection.init()

    logger.info("Starting Projection SendOrder")
    SendOrderProjection.init(orderService)

    logger.info("Starting HTTP server")
    startGrpcServer(itemPopularityRepository)
  }

  protected def newOrderServiceClientSettings(system: ActorSystem[_]): GrpcClientSettings = {
    val host = system.settings.config.getString("shopping-order-service.host")
    val port = system.settings.config.getInt("shopping-order-service.port")
    GrpcClientSettings.connectToServiceAt(host, port)(system).withTls(false)
  }

  def startGrpcServer(
      itemPopularityRepository: ItemPopularityRepositoryImpl
  )(implicit context: ShoppingCartContext): Unit = {

    implicit val system: ActorSystem[_] = context.system
    val host                            = system.settings.config.getString("shopping-cart-service.grpc.interface")
    val port                            = system.settings.config.getInt("shopping-cart-service.grpc.port")
    val grpcService                     = new ShoppingCartServiceImpl(context, itemPopularityRepository)
    val service =
      ServiceHandler.concatOrNotFound(
        ShoppingCartServiceHandler.partial(grpcService),
        ServerReflection.partial(List(ShoppingCartService))
      )
    GrpcServer.start(service, host, port)
  }
}
