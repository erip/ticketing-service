package com.github.erip.ticket.api
import java.util.UUID

import org.scalatest.{ Matchers, WordSpecLike }

class DomainSpec extends WordSpecLike with Matchers {

  "A HoldSeats object" should {
    "fail to be constructed with non-positive arguments" in {
      assertThrows[IllegalArgumentException] {
        HoldSeats(-1, UUID.randomUUID())
      }

      assertThrows[IllegalArgumentException] {
        HoldSeats(0, UUID.randomUUID())
      }
    }
  }
}
