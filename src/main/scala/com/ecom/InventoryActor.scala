package com.ecom

import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCode
import com.ecom.models.{Order, User, Shopper, WebsocketMessage}
import scala.language.postfixOps

object InventoryActor {

  sealed trait Command
  final case class ReserveProducts(userID: String, order: Order) extends Command
  final case class ShipProduct(userID: String, productID: String) extends Command


  def apply(parentActorRef: ActorRef[OrderManager.Command]): Behavior[Command] = Behaviors.setup { context =>

    implicit val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
    implicit val scheduler = context.system.scheduler
    implicit val executionContext = context.system.executionContext
    implicit val system = context.system

    Behaviors.receiveMessage[Command] {

      case ReserveProducts(userID,order) =>
        println(s"reserving products: ${order.id}")
        // Update internal state
        // Update Inventory
        // ProductsReserved
        Behaviors.same

      case ShipProduct(userID,productID) =>
        println(s"shipping product: $productID")
        // Update internal state
        // ProductShipped
        Behaviors.same

    }
  }
}
