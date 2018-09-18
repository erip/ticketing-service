package com.github.erip.ticket.api

import java.util.UUID

import scala.collection.immutable._

import play.api.libs.json.{ Format, Json }

final case class Seat(number: Int)

object Seat {
  implicit val format: Format[Seat] = Json.format
}

final case class SeatHold(
  reservationId: UUID,
  seats: Seq[Seat]
)

object SeatHold {
  implicit val format: Format[SeatHold] = Json.format
}

/**
  * @param numberOfSeats the number of seats to find and hold
  * @param customerId unique identifier for the customer
  */
final case class HoldSeats(
  numberOfSeats: Int,
  customerId: UUID
) {
  require(numberOfSeats > 0, "A positive number of seats is required")
}

object HoldSeats {
  implicit val format: Format[HoldSeats] = Json.format
}

/**
  * @param reservationId the seat hold identifier
  * @param customerId the email address of the customer to which the seat hold is assigned
  */
final case class ConfirmReservation(
  reservationId: UUID,
  customerId: UUID
)

object ConfirmReservation {
  implicit val format: Format[ConfirmReservation] = Json.format
}
