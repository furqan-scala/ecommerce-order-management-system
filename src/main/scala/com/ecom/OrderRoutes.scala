package com.ecom

import akka.http.scaladsl.model.{StatusCodes}
import akka.http.scaladsl.server.Route
import scala.concurrent.Future
import com.ecom.OrderManager._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import JsonFormats._
import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Source}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.sse.ServerSentEvent
import com.ecom.OrderActor.OrderReceivedResponse
import com.ecom.models.Order
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}


class OrderRoutes(orderManager: ActorRef[OrderManager.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def placeNewOrder(order: Order): Future[OrderReceivedResponse] =
    orderManager.ask(PlaceOrder(order, _))

  def getOrderDetail(orderID: String): Future[GetOrderDetailResponse] =
    orderManager.ask(GetOrderDetail(orderID, _))


  def closeOrder(orderID: String): Future[GetOrderClosedResponse] =
    orderManager.ask(CloseOrder(orderID, _))


  def completeOrder(orderID: String): Future[OrderCompletedResponse] =
    orderManager.ask(OrderCompleted(orderID, _))


  implicit val executionContext = system.executionContext

  val incomingWebSocket =
    Flow[Message]
      .collect {
        case TextMessage.Strict(text) =>
          Future.successful(text)

        case TextMessage.Streamed(textStream) =>
          textStream.runFold("")(_ + _)
            .flatMap(Future.successful)
      }
      .mapAsync(1)(identity)
      .groupedWithin(1000, 1 second)
      .map(messages => messages.last)
      .mapAsync(1) {
        case (lastMessage) =>
          orderManager.ask(ReceivedWebsocketMessage(lastMessage, _))
            .mapTo[OrderManager.ReceivedWebsocketMessageResponseDone]
            .map(_ => lastMessage)
      }
      .mapConcat(_ => Nil)


  sealed trait Protocol
  case class SocketMessage(msg: String) extends Protocol
  case object Complete extends Protocol
  case class Fail(ex: Exception) extends Protocol



  val echoService: Flow[Message, Message, _] = Flow[Message].map {
    case TextMessage.Strict(txt) => {
      println(s"ECHO: $txt")
      TextMessage("ECHO: " + txt)
    }
    case _ => TextMessage("Message type unsupported")
  }

  val orderRoutes: Route =
    pathPrefix("orders") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[Order]) { order =>
                onSuccess(placeNewOrder(order)) { response =>
                  complete(StatusCodes.Created, response)
                }
              }
            })
        },
        post {
          path("close") {
            entity(as[String]) { orderID =>
              onSuccess(closeOrder(orderID)) { success =>
                complete(StatusCodes.OK, success)
              }
            }
          }
        },
        post {
          path("completed") {
            entity(as[String]) { orderID =>
              onSuccess(completeOrder(orderID)) { success =>
                complete(StatusCodes.OK, success)
              }
            }
          }
        },

        path(Segment) { orderID =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(getOrderDetail(orderID)) { response =>
                  response.order match {
                    case Some(order) => complete(order)
                    case None => complete("")
                  }
                }
              }
            })
        })
    }



  val incomingSocketRoute: Route = {
    path("msg-inc") {
      get {
        handleWebSocketMessages(incomingWebSocket)
      }
    }
  }


  val outgoingSocketRoute: Route = {
    path("echo") {
      get {
        handleWebSocketMessages(echoService)
      }
    }
  }


  def eventsRoute: Route = {
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
    import java.time.LocalTime
    import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

    path("events") {
      get {
        complete {
          Source
            .tick(2.seconds, 2.seconds, NotUsed)
            .map(_ => LocalTime.now())
            .map(time => ServerSentEvent(ISO_LOCAL_TIME.format(time)))
            .keepAlive(1.second, () => ServerSentEvent.heartbeat)
        }
      }
    }
  }

  def getUTF8Encoding = {
    val str = "'id: fgh34\nevent: myEvent\ndata: This is event data\n\n'"
    val bytes = str.getBytes("UTF-8")
    val utf8EncodedString = new String(bytes,"UTF-8")
    utf8EncodedString
  }


  var payload = "payload: pay1"


  def eventsRoute2: Route = {
    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
    import java.time.LocalTime
    import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

    path("events") {
      get {
        complete {
          Source
            .tick(2.seconds, 2.seconds, NotUsed)
            .map(_ => LocalTime.now())
            .map(time => ServerSentEvent(ISO_LOCAL_TIME.format(time),Some("ev1"),Some("123"),Some(300)))
            .keepAlive(1.second, () => ServerSentEvent.heartbeat)
        }
      }
    }
  }



  val content =
    """
      |<html>
      |<head></head>
      |<body>Order Manager app akka http</body>
      |</html>
  """".stripMargin

  val welcomeRoute: Route = {
    path("welcome") {
      get {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            content
          )
        )
      }
    }
  }




}
