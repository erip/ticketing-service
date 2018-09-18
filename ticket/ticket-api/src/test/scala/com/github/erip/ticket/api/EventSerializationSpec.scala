package com.github.erip.ticket.api

import java.util.UUID

import org.scalatest.{ Matchers, WordSpecLike }
import play.api.libs.json.{ JsArray, JsString, Json }

import scala.collection.immutable._

class EventSerializationSpec extends WordSpecLike with Matchers {

  "A SeatsHeld event" should {
    "serialize according to its defined schema" in {
      val reservationId = UUID.randomUUID()
      val customerId    = UUID.randomUUID()
      val heldSeats     = Seq(Seat(1), Seat(2))

      val event          = SeatsHeld(reservationId, customerId, heldSeats)
      val jsonifiedEvent = Json.toJson(event)

      (jsonifiedEvent \ "reservationId").as[JsString] should ===(JsString(reservationId.toString))
      (jsonifiedEvent \ "customerId").as[JsString] should ===(JsString(customerId.toString))
      (jsonifiedEvent \ "heldSeats").as[JsArray] should ===(
        JsArray(heldSeats.map(Json.toJson[Seat]))
      )
    }
  }

  "A SeatsReserved event" should {
    "serialize according to its defined schema" in {
      val reservationId  = UUID.randomUUID()
      val customerId     = UUID.randomUUID()
      val confirmationId = UUID.randomUUID()

      val event          = SeatsReserved(reservationId, customerId, confirmationId)
      val jsonifiedEvent = Json.toJson(event)

      (jsonifiedEvent \ "reservationId").as[JsString] should ===(JsString(reservationId.toString))
      (jsonifiedEvent \ "customerId").as[JsString] should ===(JsString(customerId.toString))
      (jsonifiedEvent \ "confirmationId").as[JsString] should ===(JsString(confirmationId.toString))
    }
  }

}
