package shopping.cart.grpc

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object GrpcServer {

  def start(
      grpcService: HttpRequest => Future[HttpResponse],
      interface: String,
      port: Int
  )(implicit system: ActorSystem[_]): Unit = {

    implicit val ec: ExecutionContext = system.executionContext

    val bound =
      Http()
        .newServerAt(interface, port)
        .bind(grpcService)
        .map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Shopping online at gRPC server {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
