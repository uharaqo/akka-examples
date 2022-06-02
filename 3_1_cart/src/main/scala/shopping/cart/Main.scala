package shopping.cart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import shopping.cart.es.{ ShoppingCartActor, ShoppingCartCluster }
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

    logger.info("Starting ShoppingCart")
    ClusterSharding(system).init(ShoppingCartCluster.newShardedEntity())

    logger.info("Starting Projection")
    ScalikeJdbcSetup.init(system)
    val itemPopularityRepository = new ItemPopularityRepositoryImpl()
    ItemPopularityProjection.init(system, itemPopularityRepository)

    logger.info("Starting Projection PublishEvents")
    PublishEventsProjection.init(system)

    logger.info("Starting Projection SendOrder")
    SendOrderProjection.init(system, orderService)

    logger.info("Starting HTTP server")
    startGrpcServer(system, itemPopularityRepository)
  }

  protected def newOrderServiceClientSettings(system: ActorSystem[_]): GrpcClientSettings = {
    val host = system.settings.config.getString("shopping-order-service.host")
    val port = system.settings.config.getInt("shopping-order-service.port")
    GrpcClientSettings.connectToServiceAt(host, port)(system).withTls(false)
  }

  def startGrpcServer(system: ActorSystem[_], itemPopularityRepository: ItemPopularityRepositoryImpl): Unit = {
    val host        = system.settings.config.getString("shopping-cart-service.grpc.interface")
    val port        = system.settings.config.getInt("shopping-cart-service.grpc.port")
    val grpcService = new ShoppingCartServiceImpl(system, itemPopularityRepository)
    val service =
      ServiceHandler.concatOrNotFound(
        ShoppingCartServiceHandler.partial(grpcService)(system),
        ServerReflection.partial(List(ShoppingCartService))(system)
      )
    GrpcServer.start(system, service, host, port)
  }
}
