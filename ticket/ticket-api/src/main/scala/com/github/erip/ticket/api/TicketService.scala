package com.github.erip.ticket.api

import java.util.UUID

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.Method

import scala.collection.immutable._

trait TicketService extends Service {

  import Service._

  /**
    * The number of seats in the venue that are neither held nor reserved
    *
    * @return the number of tickets available in the venue
    */
  def numberOfAvailableSeats(): ServiceCall[NotUsed, Int]

  /**
    *  The ids of the remaining available seats; i.e., those which are neither
    *  held nor reserved.
    */
  def availableSeats(availability: Boolean): ServiceCall[NotUsed, Seq[Seat]]

  /**
    * Find and hold the best available seats for a customer
    *
    */
  def findAndHoldSeats(): ServiceCall[HoldSeats, SeatHold]

  /**
    * Commit seats held for a specific customer
    *
    * @return a reservation confirmation code
    */
  def reserveSeats(): ServiceCall[ConfirmReservation, UUID]

  def ticketTopic: Topic[TicketEvent]

  override def descriptor: Descriptor =
    named("ticket")
      .withCalls(
        restCall(Method.GET, "/api/ticket/seats?availability", availableSeats _),
        restCall(Method.GET, "/api/ticket/seats/length", numberOfAvailableSeats()),
        restCall(Method.POST, "/api/ticket/hold", findAndHoldSeats()),
        restCall(Method.PUT, "/api/ticket/reserve", reserveSeats())
      )
      .withTopics(
        topic(TicketService.TICKET_TOPIC, ticketTopic)
      )
      .withAutoAcl(autoAcl = true)

}

object TicketService {
  val TICKET_TOPIC = "ticket-topic"
}
