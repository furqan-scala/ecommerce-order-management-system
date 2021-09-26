package com.ecom

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.ecom.models.Order

import scala.language.postfixOps

object PaymentActor {

  sealed trait Command
  final case class ProcessPayment(userID: String, order: Order) extends Command
  final case class CheckProcessedPayment(userID: String, order: Order, replyTo: ActorRef[ProcessedPaymentResponse]) extends Command

  final case class ProcessedPaymentResponse(completed: Boolean)

  def apply(parentActorRef: ActorRef[OrderManager.Command]): Behavior[Command] = Behaviors.setup { context =>

    implicit val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
    implicit val scheduler = context.system.scheduler
    implicit val executionContext = context.system.executionContext
    implicit val system = context.system

    Behaviors.receiveMessage[Command] {

      case ProcessPayment(userID, order) =>
        println(s"processing payment: ${order.id}")
        //PaymentProcessed
        Behaviors.same

      case CheckProcessedPayment(userID,order,replyTo) =>
        // If payment processing is already completed for this order -> reply back done
        replyTo ! ProcessedPaymentResponse(true)
        Behaviors.same

    }
  }
}
