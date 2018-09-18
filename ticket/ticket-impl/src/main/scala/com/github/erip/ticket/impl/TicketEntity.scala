package com.github.erip.ticket.impl

import java.util.UUID

import com.github.erip.ticket.api._
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent,
  AggregateEventTag,
  PersistentEntity
}
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.typesafe.scalalogging.Logger
import julienrf.json.derived
import play.api.libs.json.{ Format, Json, OFormat }

import scala.collection.immutable.{ Map, Seq, Set }

final case class TicketEntityState(
  availableSeats: Seq[Seat],
  heldSeatsByCustomer: Map[String, Map[String, Set[Seat]]],
  reservedSeats: Map[String, Map[String, Set[Seat]]]
)

object TicketEntityState {

  implicit val format: Format[TicketEntityState] = Json.format

  /**
    * Initializes the available seats as a list of seats according to the following:
    *
    * If the state is:
    *
    * ```
    * Screen
    * ======
    * A B C
    * D E F
    * G H I
    * ```
    *
    * The list of available seats will be
    *
    * List(A, B, C, F, E, D, G, H, I)
    *
    * This is done so we can keep the distributed seats in-order and as close as possible
    * when crossing a row-boundary.
    *
    * When new seats are requested, we simple take them from the front of the available seat
    * list at O(1) cost. For example, requesting four seats above will yield
    *
    * List(E, D, G, H, I)
    *
    * or
    *
    * ```
    * Screen
    * ======
    * X X X
    * D E X
    * G H I
    * ```
    *
    * @param rows
    * @param columns
    * @return
    */
  def empty(
    rows: Int,
    columns: Int
  ): TicketEntityState = {
    require(rows > 0 && columns > 0, "Rows and columns must be positive")

    val availableSeats: Seq[Seat] =
      // There are [0, rows*columns) seats initially
      (0 until rows * columns)
      // The seats are in groups of size `columns` (i.e., row-size)
        .grouped(columns)
        .zipWithIndex
        .flatMap {
          case (as, ix) =>
            // If this row is an even row, just create a list from the seat number
            if (ix % 2 == 0)
              as.map(Seat.apply)
            else
              // If this row is an off row, reverse it so we can minimize distance when
              // moving from one row to another
              as.reverse.map(Seat.apply)
        }
        .toList

    TicketEntityState(availableSeats, Map.empty, Map.empty)
  }
}

class TicketEntity(
  rows: Int,
  columns: Int
) extends PersistentEntity {

  import TicketEntity._

  override type Command = InternalTicketCommand[_]
  override type Event   = InternalTicketEvent
  override type State   = TicketEntityState

  private val log: Logger = Logger[TicketEntity]

  override def initialState: TicketEntityState =
    TicketEntityState.empty(rows, columns)

  private def keyInMap[A, B](
    k: A,
    m: Map[A, B]
  ): Boolean = m.contains(k)

  private def hasPendingHold(
    customerId: UUID,
    state: TicketEntityState
  ): Boolean =
    keyInMap(customerId.toString, state.heldSeatsByCustomer)

  private def hasHold(
    customerId: UUID,
    reservationId: UUID,
    state: TicketEntityState
  ): Boolean = keyInMap(reservationId.toString, state.heldSeatsByCustomer(customerId.toString))

  private def hasEnoughRemainingSeats(
    requestedSeats: Int,
    remainingSeats: Seq[Seat]
  ): Boolean =
    requestedSeats > remainingSeats.size

  /**
    * This behavior is necessarily synchronous. Because of this, there's no need for explicit
    * synchronization. This ensures that there are no race conditions.
    *
    * Commands are issued from clients and events are persisted as facts. Other services
    * can subscribe to these events (i.e., this service is an event source) and react
    * instead of creating blocking HTTP requests to this service.
    *
    * @return The "state" the service will be in after handling the current request.
    */
  override def behavior: Behavior =
    Actions()
      .onReadOnlyCommand[InternalGetAvailableSeats.type, InternalAvailableSeats] {
        // A read-only command will not change the state.
        case (InternalGetAvailableSeats, ctx, state) =>
          ctx.reply(InternalAvailableSeats(state.availableSeats))
      }
      .onCommand[InternalHoldSeats, SeatHold] {
        // If the requesting customer already has a pending hold or there aren't enough
        // seats to service their request, fail. Otherwise, take the next `numberOfSeats` in-order.
        case (InternalHoldSeats(customerId, numberOfSeats), ctx, state) =>
          if (hasPendingHold(customerId, state)) {
            ctx.commandFailed(CustomerAlreadyHasHoldException(customerId))
            ctx.done
          } else if (hasEnoughRemainingSeats(numberOfSeats, state.availableSeats)) {
            ctx.commandFailed(
              NotEnoughSeatsRemainingException(
                numberOfSeats,
                state.availableSeats.size
              )
            )
            ctx.done
          } else {
            val reservationId         = UUID.randomUUID()
            val guestSeats: Seq[Seat] = state.availableSeats.take(numberOfSeats)
            ctx.thenPersist(InternalSeatsHeld(reservationId, customerId, guestSeats)) { _ =>
              ctx.reply(SeatHold(reservationId, guestSeats))
            }
          }

      }
      .onCommand[InternalReserveSeats, UUID] {
        // If the customer doesn't have holds or reservationId is unknown, fail. Otherwise, remove the hold
        // and confirm the reservation with the client.
        case (InternalReserveSeats(customerId, reservationId), ctx, state) =>
          log.debug("Customer's holds: {}", state.heldSeatsByCustomer(customerId.toString))

          if (!hasPendingHold(customerId, state)) {
            ctx.commandFailed(CustomerNotFoundException(customerId))
            ctx.done
          } else if (!hasHold(customerId, reservationId, state)) {
            // If reservation doesn't exist, throw...

            ctx.commandFailed(ReservationNotFoundException(customerId))
            ctx.done
          } else {

            val confirmationId: UUID = UUID.randomUUID()

            ctx.thenPersist(
              InternalSeatsReserved(customerId, reservationId, confirmationId)
            ) { _ =>
              ctx.reply(confirmationId)
            }
          }

      }
      .onEvent {
        case (InternalSeatsHeld(reservationId, customerId, seats), state) =>
          // Remove the held seats from the available seats list
          val availableSeatsAfterHold = state.availableSeats.drop(seats.length)
          val heldSeatsByCustomerAfterHold: Map[String, Map[String, Set[Seat]]] =
            state.heldSeatsByCustomer + (customerId.toString -> Map(
              reservationId.toString -> seats.toSet
            ))

          state.copy(
            availableSeats = availableSeatsAfterHold,
            heldSeatsByCustomer = heldSeatsByCustomerAfterHold
          )
        case (InternalSeatsReserved(customerId, reservationId, confirmationId), state) =>
          val heldSeatsForThisReservation =
            state.heldSeatsByCustomer(customerId.toString)(reservationId.toString)

          val heldSeatsWithoutWithoutConfirmation = state.heldSeatsByCustomer - customerId.toString

          val customersReservations: Map[String, Set[Seat]] = Map(
            reservationId.toString -> heldSeatsForThisReservation
          )

          val reservationsWithNewConfirmation: Map[String, Map[String, Set[Seat]]] =
            state.reservedSeats.updated(customerId.toString, customersReservations)

          state.copy(
            heldSeatsByCustomer = heldSeatsWithoutWithoutConfirmation,
            reservedSeats = reservationsWithNewConfirmation
          )
      }
}

object TicketEntity {
  def PersistenceId = "ticket-entity"

  sealed trait InternalTicketEvent extends AggregateEvent[InternalTicketEvent] {
    def aggregateTag: AggregateEventTag[InternalTicketEvent] = InternalTicketEvent.Tag
  }

  final case class InternalSeatsHeld(
    reservationId: UUID,
    customerId: UUID,
    seats: Seq[Seat]
  ) extends InternalTicketEvent

  object InternalSeatsHeld {
    implicit val format: Format[InternalSeatsHeld] = Json.format
  }

  final case class InternalSeatsReserved(
    customerId: UUID,
    reservationId: UUID,
    confirmationId: UUID
  ) extends InternalTicketEvent

  object InternalSeatsReserved {
    implicit val format: Format[InternalSeatsReserved] = Json.format
  }

  object InternalTicketEvent {
    implicit val format: OFormat[InternalTicketEvent] = derived.oformat()
    val Tag: AggregateEventTag[InternalTicketEvent]   = AggregateEventTag[InternalTicketEvent]
  }

  sealed trait InternalTicketCommand[R] extends ReplyType[R]

  final case class InternalAvailableSeats(seats: Seq[Seat])

  object InternalAvailableSeats {
    implicit val format: Format[InternalAvailableSeats] = Json.format
  }

  final case object InternalGetAvailableSeats
      extends InternalTicketCommand[InternalAvailableSeats] {
    implicit val format: OFormat[InternalGetAvailableSeats.type] = derived.oformat()
  }

  final case class InternalHoldSeats(
    customerId: UUID,
    numberOfSeats: Int
  ) extends InternalTicketCommand[SeatHold]

  object InternalHoldSeats {
    implicit val format: Format[InternalHoldSeats] = Json.format
  }

  final case class InternalReserveSeats(
    customerId: UUID,
    reservationId: UUID
  ) extends InternalTicketCommand[UUID]

  object InternalReserveSeats {
    implicit val format: Format[InternalReserveSeats] = Json.format
  }

  object InternalTicketCommand {
    implicit val format: OFormat[InternalTicketCommand[_]] = derived.oformat()
  }

  def serializers: Seq[JsonSerializer[_]] = Seq(
    // Domain
    JsonSerializer[Seat],
    JsonSerializer[SeatHold],
    JsonSerializer[HoldSeats],
    JsonSerializer[ConfirmReservation],
    // Events
    JsonSerializer[SeatsHeld],
    JsonSerializer[SeatsReserved],
    JsonSerializer[TicketEvent],
    // Internal state
    JsonSerializer[InternalAvailableSeats],
    JsonSerializer[TicketEntityState],
    // Internal commands
    JsonSerializer[InternalGetAvailableSeats.type],
    JsonSerializer[InternalHoldSeats],
    JsonSerializer[InternalReserveSeats],
    JsonSerializer[InternalTicketCommand[_]],
    // Internal events
    JsonSerializer[InternalSeatsHeld],
    JsonSerializer[InternalSeatsReserved],
    JsonSerializer[InternalTicketEvent]
  )

}
