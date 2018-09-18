package com.github.erip.ticket.impl
import java.util.UUID

import akka.NotUsed
import com.github.erip.ticket.api._
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{
  EventStreamElement,
  PersistentEntityRef,
  PersistentEntityRegistry
}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future }

class TicketServiceImpl(registry: PersistentEntityRegistry)(implicit ec: ExecutionContext)
    extends TicketService {

  private def ticketSaleEntity: PersistentEntityRef[TicketEntity#Command] =
    registry.refFor[TicketEntity](TicketEntity.PersistenceId)

  /**
    * The number of seats in the venue that are neither held nor reserved
    *
    * @return the number of tickets available in the venue
    */
  override def numberOfAvailableSeats: ServiceCall[NotUsed, Int] = ServerServiceCall { _ =>
    // Get the list of available seats and map `List#length` on it once its ready.
    for {
      seats <- this.availableSeats(availability = true).invoke()
    } yield seats.size
  }

  /**
    *  The ids of the remaining available seats; i.e., those which are neither
    *  held nor reserved.
    */
  override def availableSeats(availability: Boolean): ServiceCall[NotUsed, Seq[Seat]] =
    ServerServiceCall { _ =>
      if (!availability) {
        Future.failed(
          new IllegalArgumentException(
            "There is currently no support for browsing unavailable seats."
          )
        )
      } else {
        ticketSaleEntity.ask(TicketEntity.InternalGetAvailableSeats).map(_.seats)
      }
    }

  /**
    * Find and hold the best available seats for a customer
    *
    */
  override def findAndHoldSeats(): ServiceCall[HoldSeats, SeatHold] = ServerServiceCall { req =>
    ticketSaleEntity.ask(TicketEntity.InternalHoldSeats(req.customerId, req.numberOfSeats))
  }

  /**
    * Commit seats held for a specific customer
    *
    * @return a reservation confirmation code
    */
  override def reserveSeats: ServiceCall[ConfirmReservation, UUID] = ServerServiceCall { req =>
    ticketSaleEntity.ask(TicketEntity.InternalReserveSeats(req.customerId, req.reservationId))
  }

  private def convertEvent(
    internalTicketEvent: EventStreamElement[TicketEntity.InternalTicketEvent]
  ): TicketEvent =
    internalTicketEvent.event match {
      case TicketEntity.InternalSeatsHeld(reservationId, customerId, seats) =>
        SeatsHeld(reservationId, customerId, seats)
      case TicketEntity.InternalSeatsReserved(customerId, reservationId, confirmationId) =>
        SeatsReserved(reservationId, customerId, confirmationId)
    }

  override def ticketTopic: Topic[TicketEvent] = TopicProducer.singleStreamWithOffset {
    fromOffset =>
      registry
        .eventStream(TicketEntity.InternalTicketEvent.Tag, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
  }

}
