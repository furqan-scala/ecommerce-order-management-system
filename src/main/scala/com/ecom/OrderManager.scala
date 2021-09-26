package com.ecom

import akka.actor.typed.scaladsl.AskPattern.{Askable}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors}
import akka.util.Timeout
import com.ecom.OrderActor.{GracefulShutdown, OrderReceived}
import scala.collection.mutable.Map
import scala.util.{Failure, Success}
import com.ecom.JsonFormats._
import spray.json._
import akka.NotUsed
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.stream.scaladsl.Source
import com.ecom.models.{Order, WebsocketMessage}


//Supervisor
object OrderManager {

  sealed trait Command
  final case class PlaceOrder(order: Order, replyTo: ActorRef[OrderActor.OrderReceivedResponse]) extends Command
  final case class GetOrderDetail(orderID: String, replyTo: ActorRef[GetOrderDetailResponse]) extends Command
  final case class CloseOrder(orderID: String, replyTo: ActorRef[GetOrderClosedResponse]) extends Command
  final case class OrderCompleted(orderID: String, replyTo: ActorRef[OrderCompletedResponse]) extends Command

  case class ReceivedWebsocketMessageResponseDone() extends Command
  final case class ReceivedWebsocketMessage(message: String, replyTo: ActorRef[OrderManager.ReceivedWebsocketMessageResponseDone]) extends Command

  final case class OrderSSESourceResponse(source: Source[ServerSentEvent, NotUsed])
  final case class SendWebsocketMessage(message: WebsocketMessage) extends Command

  final case class OrderPerformed(description: String)
  final case class GetOrderDetailResponse(order: Option[Order])
  final case class GetOrderClosedResponse(success: Boolean)
  final case class OrderCompletedResponse(success: Boolean)


  def apply(): Behavior[Command] = {

    Behaviors.setup[Command] { context =>

      val orders: Map[String, ActorRef[OrderActor.Command]] = Map()

      implicit val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
      implicit val scheduler = context.system.scheduler
      implicit val executionContext = context.system.executionContext


      val inventoryActor = context.spawn(InventoryActor(context.self), s"inventory-${java.util.UUID.randomUUID.toString}")
      val paymentActor = context.spawn(PaymentActor(context.self), s"payment-${java.util.UUID.randomUUID.toString}")


      Behaviors.receiveMessage[Command] {
        case PlaceOrder(order, replyTo) =>
          val orderID = java.util.UUID.randomUUID.toString
          val orderActor = context.spawn(OrderActor(orderID,context.self,inventoryActor,paymentActor), s"order-$orderID")
          orders(orderID) = orderActor
          val future = orderActor.ask(OrderReceived(orderID,order, _))
          future.onComplete {
            case Success(response) => replyTo ! response
            case Failure(ex)       => println(s"Error! didn't placed order: ${ex.getMessage}")
          }

          Behaviors.same

        case GetOrderDetail(orderID, replyTo) =>
          orders.get(orderID) match {
            case Some(orderActor) => {
              val future = orderActor.ask(OrderActor.GetOrderDetail(_))
              future.onComplete {
                case Success(OrderActor.OrderDetailResponse(order)) => replyTo ! GetOrderDetailResponse(Some(order))
                case Failure(ex)                          => println(s"oops! didn't get order detail from order actor: ${ex.getMessage}")
              }
            }
            case None => replyTo ! GetOrderDetailResponse(None)
          }
          Behaviors.same


        case OrderCompleted(orderID, replyTo) =>
          orders.get(orderID) match {
            case Some(orderActor) => {
              orders.remove(orderID)
              orderActor ! OrderActor.OrderCompleted
              replyTo ! OrderCompletedResponse(true)
            }
            case None => replyTo ! OrderCompletedResponse(false)
          }
          Behaviors.same


        case CloseOrder(orderID, replyTo) =>
          orders.get(orderID) match {
            case Some(orderActor) => {
              orders.remove(orderID)
              orderActor ! GracefulShutdown
              replyTo ! GetOrderClosedResponse(true)
            }
            case None => replyTo ! GetOrderClosedResponse(false)
          }
          Behaviors.same

        case ReceivedWebsocketMessage(message, replyTo) =>
          println(s"OrderManager -> ReceivedWebsocketMessage: $message")
          replyTo ! ReceivedWebsocketMessageResponseDone()

          try {
            val jsonWebsocketMessage = message.parseJson
            jsonWebsocketMessage match {
              case JsObject(fields) => {
                val websocketMessage = jsonWebsocketMessage.convertTo[WebsocketMessage]
                orders.get(websocketMessage.id) match {
                  case Some(orderActor) => orderActor ! OrderActor.ReceivedWebsocketMessage(websocketMessage)
                  case None =>
                }
              }
              case _  =>
            }
          } catch {
            case _ => {
              println("json parsing failed")
            }
          }

          Behaviors.same


        case SendWebsocketMessage(message) =>
          println(s"OrderManager -> SendWebsocketMessage(message): $message")
          Behaviors.same

      }
    }
  }


}
