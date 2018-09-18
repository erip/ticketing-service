package com.github.erip.ticket.api

import java.util.UUID

import org.scalatest.{ Matchers, WordSpecLike }
import play.api.libs.json.{ JsArray, JsNumber, JsString, Json }

import scala.collection.immutable._

class DomainSerializationSpec extends WordSpecLike with Matchers {

  "A SeatHold object" should {
    "according to its defined schema" in {
      val reservationId = UUID.randomUUID()
      val seats         = Seq(Seat(1), Seat(2))

      val domainObject          = SeatHold(reservationId, seats)
      val jsonifiedDomainObiect = Json.toJson(domainObject)

      (jsonifiedDomainObiect \ "reservationId").as[JsString] should ===(
        JsString(reservationId.toString)
      )
      (jsonifiedDomainObiect \ "seats").as[JsArray] should ===(
        JsArray(seats.map(Json.toJson[Seat]))
      )
    }
  }

  "A HoldSeats object" should {
    "according to its defined schema" in {
      val customerId    = UUID.randomUUID()
      val numberOfSeats = 6

      val domainObject          = HoldSeats(numberOfSeats, customerId)
      val jsonifiedDomainObiect = Json.toJson(domainObject)

      (jsonifiedDomainObiect \ "customerId").as[JsString] should ===(
        JsString(customerId.toString)
      )
      (jsonifiedDomainObiect \ "numberOfSeats").as[JsNumber] should ===(
        JsNumber(numberOfSeats)
      )
    }
  }

  "A ConfirmReservation object" should {
    "according to its defined schema" in {
      val reservationId = UUID.randomUUID()
      val customerId    = UUID.randomUUID()

      val domainObject          = ConfirmReservation(reservationId, customerId)
      val jsonifiedDomainObiect = Json.toJson(domainObject)

      (jsonifiedDomainObiect \ "reservationId").as[JsString] should ===(
        JsString(reservationId.toString)
      )
      (jsonifiedDomainObiect \ "customerId").as[JsString] should ===(
        JsString(customerId.toString)
      )
    }
  }

}
