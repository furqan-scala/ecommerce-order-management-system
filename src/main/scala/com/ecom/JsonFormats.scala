package com.ecom

import com.ecom.OrderActor.OrderReceivedResponse
import com.ecom.OrderManager.{GetOrderClosedResponse, OrderCompletedResponse, OrderPerformed}
import com.ecom.models._
//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._


  implicit val orderPerformedJsonFormat = jsonFormat1(OrderPerformed)

  implicit val orderClosedResponse = jsonFormat1(GetOrderClosedResponse)
  implicit val orderCompletedResponse = jsonFormat1(OrderCompletedResponse)

  implicit val orderReceivedResponse = jsonFormat2(OrderReceivedResponse)

  //shopper
  implicit val shopperJsonFormat = jsonFormat2(Shopper)

  //  //WebsocketMessage
  implicit val websocketMessageJsonFormat = jsonFormat3(WebsocketMessage)


  //SSEMessage
  implicit val sseMessageJsonFormat = jsonFormat4(SSEMessage)


  //Order
  implicit val productJsonFormat = jsonFormat6(Product)
  implicit val cartItemJsonFormat = jsonFormat3(CartItem)
  implicit val geoPointLocationJsonFormat = jsonFormat2(GeoPointLocation)
  implicit val userJsonFormat = jsonFormat5(User)
  implicit val storeJsonFormat = jsonFormat5(Store)

  implicit val orderJsonFormat = jsonFormat4(Order)


  //Users
  implicit val usersJsonFormat = jsonFormat1(Users)


}
//#json-formats
