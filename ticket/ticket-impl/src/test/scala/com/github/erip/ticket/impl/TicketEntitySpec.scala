package com.github.erip.ticket.impl

import java.util.UUID

import akka.actor.ActorSystem
import com.github.erip.ticket.api._
import com.github.erip.ticket.impl.TicketEntity.InternalAvailableSeats
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver.Reply
import org.scalatest.LoneElement._
import org.scalatest.{ Assertion, BeforeAndAfterAll, Matchers, WordSpecLike }

class TicketEntitySpec extends WordSpecLike with Matchers with BeforeAndAfterAll {

  private val system = ActorSystem(
    "TicketEntitySpec",
    JsonSerializerRegistry.actorSystemSetupFor(TicketSerializerRegistry)
  )

  private def assertEntityStateThrowsWithDimensions(
    rows: Int,
    columns: Int
  ): Assertion = assertThrows[IllegalArgumentException] {
    TicketEntityState.empty(rows, columns)
  }

  private def withDriver(
    rows: Int,
    columns: Int
  )(block: PersistentEntityTestDriver[
      TicketEntity#Command,
      TicketEntity#Event,
      TicketEntity#State
    ] => Assertion
  ): Assertion = {
    val driver = new PersistentEntityTestDriver(
      system,
      new TicketEntity(rows, columns),
      TicketEntity.PersistenceId
    )

    // Run the tests
    block(driver)
  }

  "A TicketEntityState" should {
    "fail to be constructed with non-positive dimensions" in {
      assertEntityStateThrowsWithDimensions(0, 0)
      assertEntityStateThrowsWithDimensions(0, 1)
      assertEntityStateThrowsWithDimensions(1, 0)
      assertEntityStateThrowsWithDimensions(-1, -1)
      assertEntityStateThrowsWithDimensions(-1, 0)
      assertEntityStateThrowsWithDimensions(0, -1)

    }
  }

  private val rows    = 5
  private val columns = 6

  private def successfullyMakeReservation(
    driver: PersistentEntityTestDriver[TicketEntity#Command, TicketEntity#Event, TicketEntity#State]
  )(customerId: UUID,
    numberOfSeats: Int,
    seatsUsed: Int = 0
  ): UUID = {
    val request  = TicketEntity.InternalHoldSeats(customerId, numberOfSeats)
    val response = driver.run(request)

    response.events.loneElement shouldBe a[TicketEntity.InternalSeatsHeld]
    response.issues shouldBe empty
    response.sideEffects.loneElement shouldBe a[Reply]
    response.replies.loneElement shouldBe an[SeatHold]

    val seatHold      = response.replies.loneElement.asInstanceOf[SeatHold]
    val reservationId = seatHold.reservationId

    seatHold.seats.size shouldBe numberOfSeats

    response.state.availableSeats.size shouldBe (rows * columns) - numberOfSeats - seatsUsed
    response.state
      .heldSeatsByCustomer(customerId.toString)(reservationId.toString)
      .size shouldBe numberOfSeats
    response.state.reservedSeats shouldBe empty

    reservationId
  }

  "A TicketEntity" should {
    "show only available seats" in withDriver(rows, columns) { driver =>
      val request  = TicketEntity.InternalGetAvailableSeats
      val response = driver.run(request)

      response.events shouldBe empty
      response.issues shouldBe empty
      response.sideEffects.loneElement shouldBe a[Reply]
      response.replies.loneElement shouldBe an[InternalAvailableSeats]
      val availableSeats = response.replies.loneElement.asInstanceOf[InternalAvailableSeats]
      availableSeats.seats.size shouldBe rows * columns
    }

    "reflect a seat hold" in withDriver(rows, columns) { driver =>
      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      val reservationId =
        successfullyMakeReservation(driver)(customerId, numberOfSeats)
      reservationId shouldBe a[UUID]
    }

    "not allow a customer to have two simultaneous holds" in withDriver(rows, columns) { driver =>
      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      val reservationId =
        successfullyMakeReservation(driver)(customerId, numberOfSeats)

      val request = TicketEntity.InternalHoldSeats(customerId, numberOfSeats)

      // Issue the request again
      val secondResponse = driver.run(request)
      secondResponse.replies.loneElement shouldBe a[CustomerAlreadyHasHoldException]
      secondResponse.sideEffects.loneElement shouldBe a[Reply]
      secondResponse.events shouldBe empty

      // Ensure there's been no state change
      secondResponse.state.availableSeats.size shouldBe (rows * columns) - numberOfSeats
      secondResponse.state
        .heldSeatsByCustomer(customerId.toString)(reservationId.toString)
        .size shouldBe numberOfSeats
      secondResponse.state.reservedSeats shouldBe empty
    }

    "not allow a customer to request more seats than exist" in withDriver(rows, columns) { driver =>
      val customerId    = UUID.randomUUID()
      val numberOfSeats = rows * columns + 1

      val request  = TicketEntity.InternalHoldSeats(customerId, numberOfSeats)
      val response = driver.run(request)

      response.replies.loneElement shouldBe a[NotEnoughSeatsRemainingException]
      response.sideEffects.loneElement shouldBe a[Reply]
      response.events shouldBe empty

      // Ensure there's been no state change
      response.state.availableSeats.size shouldBe rows * columns
      response.state.heldSeatsByCustomer shouldBe empty

      response.state.reservedSeats shouldBe empty
    }

    "not allow a customer to confirm a reservation before a hold exists" in withDriver(
      rows,
      columns
    ) { driver =>
      val customerId    = UUID.randomUUID()
      val reservationId = UUID.randomUUID()

      val request  = TicketEntity.InternalReserveSeats(customerId, reservationId)
      val response = driver.run(request)

      response.replies.loneElement shouldBe a[CustomerNotFoundException]
      response.state.availableSeats.size shouldBe rows * columns
      response.state.heldSeatsByCustomer shouldBe empty
      response.state.reservedSeats shouldBe empty
    }

    "return an error when confirming a non-existent reservation" in withDriver(
      rows,
      columns
    ) { driver =>
      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      val _ = successfullyMakeReservation(driver)(customerId, numberOfSeats)

      val reservationId = UUID.randomUUID()

      val request  = TicketEntity.InternalReserveSeats(customerId, reservationId)
      val response = driver.run(request)

      response.replies.loneElement shouldBe a[ReservationNotFoundException]
      response.state.availableSeats.size shouldBe rows * columns - numberOfSeats
      response.state.heldSeatsByCustomer.size shouldBe 1
      response.state.reservedSeats shouldBe empty
    }

    "allow a customer to confirm a reservation after a hold has been made" in withDriver(
      rows,
      columns
    ) { driver =>
      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      val reservationId = successfullyMakeReservation(driver)(customerId, numberOfSeats)

      val request  = TicketEntity.InternalReserveSeats(customerId, reservationId)
      val response = driver.run(request)

      response.replies.loneElement shouldBe a[UUID]
      response.state.availableSeats.size shouldBe rows * columns - numberOfSeats
      response.state.heldSeatsByCustomer shouldBe empty
      response.state.reservedSeats.size shouldBe 1

      val reservationForCustomer = response.state.reservedSeats(customerId.toString)
      reservationForCustomer.values.loneElement.size shouldBe numberOfSeats
    }
  }

}
