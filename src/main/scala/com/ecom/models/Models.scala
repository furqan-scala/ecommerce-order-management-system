package com.ecom.models

import scala.collection.immutable


final case class Shopper(id: String, name: String)


final case class SSEMessage(data: String, event: Option[String], id: Option[String], retry: Option[Int])

final case class DPMessage(id: String, event: String, data: String)



final case class Product(id: String, name: String, categoryID: String, var price: Double, priceUnit: String, thumbnailURL: String)
final case class CartItem(id: String, product: Product, quantity: Int)

final case class GeoPointLocation(latitude: Double, longitude: Double)
final case class User(id: String, name: Option[String], address: String, location: Option[GeoPointLocation], deviceToken: Option[String])
final case class Store(id: String, name: String, address: String, imageUrl: String, location:  GeoPointLocation)

final case class Order(id: String, cartItems: Seq[CartItem], store: Store, user: User)

final case class WebsocketMessage(id: String, event: String, data: String)


final case class Users(users: immutable.Seq[User])
