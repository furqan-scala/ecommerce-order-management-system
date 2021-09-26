package com.ecom

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.ByteString
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Failure
import scala.util.Success

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val host = "localhost"
    val port = sys.env.getOrElse("PORT", "8080").toInt

    val pingCounter = new AtomicInteger()

    val futureBinding = Http().newServerAt(host, port)
      .adaptSettings(_.mapWebsocketSettings(_.withPeriodicKeepAliveData(() => ByteString(s"debug-${pingCounter.incrementAndGet()}"))))
      .bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }



  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val orderManagerActor = context.spawn(OrderManager(), "OrderManagerActor")
      context.watch(orderManagerActor)

      val routes = new OrderRoutes(orderManagerActor)(context.system)
      val combineRoutes = routes.welcomeRoute ~ routes.orderRoutes ~ routes.incomingSocketRoute ~ routes.outgoingSocketRoute ~ routes.eventsRoute2

      startHttpServer(combineRoutes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    //#server-bootstrapping
  }
}
//#main-class
