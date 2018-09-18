package com.github.erip.ticket.api
import java.util.UUID

import com.lightbend.lagom.scaladsl.api.transport.{ TransportErrorCode, TransportException }

final case class CustomerAlreadyHasHoldException(customerId: UUID)
    extends TransportException(
      errorCode = TransportErrorCode.BadRequest,
      message = s"Customer $customerId already has a hold on seats"
    )

final case class CustomerAlreadyHasReservationException(customerId: UUID)
    extends TransportException(
      errorCode = TransportErrorCode.BadRequest,
      message = s"Customer $customerId already has a reservation"
    )

final case class NotEnoughSeatsRemainingException(
  seatsRequsted: Int,
  seatsAvailable: Int
) extends TransportException(
      errorCode = TransportErrorCode.BadRequest,
      message = {
        val oneSeatLeft      = seatsAvailable == 1
        val oneSeatRequested = seatsRequsted == 1
        s"There ${if (oneSeatLeft) "is" else "are"} $seatsAvailable ${if (oneSeatLeft) "seat"
        else "seats"} available; could not hold $seatsRequsted ${if (oneSeatRequested) "seat"
        else "seats"}."
      }
    )

final case class CustomerNotFoundException(customerId: UUID)
    extends TransportException(
      errorCode = TransportErrorCode.NotFound,
      message = s"Customer $customerId is unknown"
    )

final case class ReservationNotFoundException(reservationId: UUID)
    extends TransportException(
      errorCode = TransportErrorCode.NotFound,
      message = s"Reservation $reservationId is unknown"
    )
