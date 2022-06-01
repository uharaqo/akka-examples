package shopping.order

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory
import shopping.cart.grpc.GrpcServer
import shopping.order.grpc.ShoppingOrderServiceImpl

import scala.util.control.NonFatal

object Main {

  private val logger = LoggerFactory.getLogger("shopping.order.Main")

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Behaviors.empty, "ShoppingOrderService")
    try init(system)
    catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    val grpcInterface =
      system.settings.config.getString("shopping-order-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("shopping-order-service.grpc.port")
    val grpcService = new ShoppingOrderServiceImpl
    val service =
      ServiceHandler.concatOrNotFound(
        proto.ShoppingOrderServiceHandler.partial(grpcService)(system),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(proto.ShoppingOrderService))(system)
      )
    GrpcServer.start(system, service, grpcInterface, grpcPort)
  }
}
