package com.github.erip.ticket.impl

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.testkit.scaladsl.TestSink
import com.github.erip.ticket.api._
import com.lightbend.lagom.scaladsl.api.AdditionalConfiguration
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.{ ServiceTest, TestTopicComponents }
import org.scalatest.{ Assertion, AsyncWordSpecLike, Matchers }
import play.api.Configuration

import scala.concurrent.Future

class TicketServiceSpec extends AsyncWordSpecLike with Matchers {

  private val rows    = 5
  private val columns = 6

  private def withClient(
    block: TicketService => (ActorSystem, Materializer) => Future[Assertion]
  )(
  ): Future[Assertion] =
    ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra(enabled = true)) { ctx =>
      new TicketApplication(ctx) with LocalServiceLocator with TestTopicComponents {

        override def additionalConfiguration: AdditionalConfiguration =
          super.additionalConfiguration ++ Configuration.from(
            Map(
              "ticketing.rows"    -> rows,
              "ticketing.columns" -> columns
            )
          )

      }
    } { server =>
      val client = server.serviceClient.implement[TicketService]
      block(client)(server.actorSystem, server.materializer)
    }

  "A TicketService" should {

    "fail to return unavailable seats" in withClient { client => (_, _) =>
      val f = client
        .availableSeats(availability = false)
        .invoke()

      (for {
        resp <- f
      } yield {
        fail("This should not succeed because availability=false is unsupported")
      }).recover {
        case t: Throwable => succeed
      }

    }

    "return the available seats" in withClient { client => (_, _) =>
      client.availableSeats(availability = true).invoke().map { seats =>
        seats.size shouldBe rows * columns
      }
    }

    "return the number of available seats" in withClient { client => (_, _) =>
      client.numberOfAvailableSeats().invoke().map { numberOfSeats =>
        numberOfSeats shouldBe rows * columns
      }
    }

    "allow a user to make a hold" in withClient { client => (system, mat) =>
      val events = client.ticketTopic.subscribe.atMostOnceSource

      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      for {
        hold  <- client.findAndHoldSeats().invoke(HoldSeats(numberOfSeats, customerId))
        seats <- client.availableSeats(availability = true).invoke()
      } yield {

        events
          .runWith(TestSink.probe[TicketEvent](system))(mat)
          .request(1)
          .expectNext(SeatsHeld(hold.reservationId, customerId, hold.seats))

        hold.seats.size shouldBe numberOfSeats
        seats.intersect(hold.seats) shouldBe empty

        seats.size shouldBe (rows * columns) - hold.seats.size
      }

    }

    "allow a user to confirm a hold" in withClient { client => (system, mat) =>
      val events = client.ticketTopic.subscribe.atMostOnceSource

      val customerId    = UUID.randomUUID()
      val numberOfSeats = 5

      for {
        hold  <- client.findAndHoldSeats().invoke(HoldSeats(numberOfSeats, customerId))
        seats <- client.availableSeats(availability = true).invoke()
        confirmation <- client
          .reserveSeats()
          .invoke(ConfirmReservation(hold.reservationId, customerId))
      } yield {

        events
          .runWith(TestSink.probe[TicketEvent](system))(mat)
          .request(2)
          .expectNext(
            SeatsHeld(hold.reservationId, customerId, hold.seats),
            SeatsReserved(hold.reservationId, customerId, confirmation)
          )

        hold.seats.size shouldBe numberOfSeats
        seats.intersect(hold.seats) shouldBe empty

        seats.size shouldBe (rows * columns) - hold.seats.size
      }
    }
  }
}
