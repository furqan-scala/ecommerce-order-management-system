package com.ecom

import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import akka.http.scaladsl.model.StatusCode
import com.ecom.models.{Order, Shopper, User, WebsocketMessage}

import scala.language.postfixOps
import com.ecom.PaymentActor
import com.ecom.PaymentActor.{CheckProcessedPayment, ProcessedPaymentResponse}

import scala.util.{Failure, Success}

object OrderActor {

  sealed trait Command
  final case class OrderReceived(orderID: String, order: Order, replyTo: ActorRef[OrderReceivedResponse]) extends Command
  final case class GetOrderDetail(replyTo: ActorRef[OrderDetailResponse]) extends Command
  final case class ProductPriceChanged(productID: String) extends Command
  case object OrderCompleted extends Command

  final case class ReceivedWebsocketMessage(websocketMessage: WebsocketMessage) extends Command
  final case class ReceivedWebsocketMessageResponse(description: String)
  case object GracefulShutdown extends Command


  // Websocket Messages Commands
  final case object Upgraded extends Command
  final case object Connected extends Command
  final case object Terminated extends Command
  final case class ConnectionFailure(ex: Throwable) extends Command
  final case class FailedUpgrade(statusCode: StatusCode) extends Command

  final case class OrderReceivedResponse(orderID: String, description: String)
  final case class OrderDetailResponse(order: Order)


  def apply(id: String, parentActorRef: ActorRef[OrderManager.Command], inventory: ActorRef[InventoryActor.Command], payment: ActorRef[PaymentActor.Command]): Behavior[Command] = Behaviors.setup { context =>

    var user: User = null
    var order: Order = Order("0", Seq.empty, null, null)
    var isShipped = false

    implicit val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
    implicit val scheduler = context.system.scheduler
    implicit val executionContext = context.system.executionContext
    implicit val system = context.system


    Behaviors.receiveMessage[Command] {

      case OrderReceived(orderID, orderValue, replyTo) =>
        replyTo ! OrderReceivedResponse(orderID, "order is placed successfully")
        order = Order(orderID,orderValue.cartItems, orderValue.store, orderValue.user)
        inventory ! InventoryActor.ReserveProducts(user.id,order)
        Behaviors.same


      case ProductPriceChanged(productID) =>
        val future = payment.ask(PaymentActor.CheckProcessedPayment(user.id,order,_))
        future.onComplete {
          case Success(PaymentActor.ProcessedPaymentResponse(completed)) => {
              if (!completed) {
                // If payment is still not processed
                // Get the updated priced for this product from Database
                val newPrice: Double = 20.0
                // update the product price for the order
                order.cartItems.foreach(cartItem => {
                    if (cartItem.product.id == productID) {
                          cartItem.product.price = newPrice
                    }
                })
              }
          }
          case Failure(ex)  => println(s"oops! didn't get order detail from order actor: ${ex.getMessage}")
        }
        Behaviors.same

      case GetOrderDetail(replyTo) =>
        println(order)
        replyTo ! OrderDetailResponse(order)
        Behaviors.same

      case OrderCompleted =>
        //save data to db
        println("OrderActor -> OrderCompleted")
//        DBManager.addOrder(order)
        Behaviors.stopped

      case ReceivedWebsocketMessage(websocketMessage) =>
        println(s"OrderActor -> ReceivedWebsocketMessage: $websocketMessage")
        println(s"websocketMessage.event : $websocketMessage.event")
        Behaviors.same


      case GracefulShutdown =>
        Behaviors.stopped


      case Upgraded =>
        println(s"$id : WebSocket upgraded")
        Behaviors.same
      case FailedUpgrade(statusCode) =>
        println(s"$id : Failed to upgrade WebSocket connection : $statusCode")
        Behaviors.same

      case ConnectionFailure(ex) =>
        println(s"$id : Failed to establish WebSocket connection $ex")
        Behaviors.same

      case Connected =>
        println(s"$id : WebSocket connected")
        Behaviors.same

      case Terminated =>
        println(s"$id : WebSocket terminated")
        Behaviors.same

    }.receiveSignal {
      case(_, PostStop) =>
        println(s"stopping actor")
        order = null
        println(s"$id : Stopping WebSocket connection")
        //          webSocket.killSwitch.shutdown()
        Behaviors.same
    }

  }

}
