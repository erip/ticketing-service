package com.github.erip.ticket.api

import java.util.UUID

import julienrf.json.derived
import play.api.libs.json.{ Format, Json, OFormat }

import scala.collection.immutable._

sealed trait TicketEvent

final case class SeatsHeld(
  reservationId: UUID,
  customerId: UUID,
  heldSeats: Seq[Seat]
) extends TicketEvent

object SeatsHeld {
  implicit val format: Format[SeatsHeld] = Json.format
}

final case class SeatsReserved(
  reservationId: UUID,
  customerId: UUID,
  confirmationId: UUID
) extends TicketEvent

object SeatsReserved {
  implicit val format: Format[SeatsReserved] = Json.format
}

object TicketEvent {
  implicit val format: OFormat[TicketEvent] = derived.oformat()
}
