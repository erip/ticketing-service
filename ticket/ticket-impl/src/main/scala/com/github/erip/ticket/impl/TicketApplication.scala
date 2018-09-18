package com.github.erip.ticket.impl

import com.github.erip.ticket.api.TicketService
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

abstract class TicketApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with AhcWSComponents
    with CassandraPersistenceComponents {

  override lazy val lagomServer            = serverFor[TicketService](wire[TicketServiceImpl])
  override lazy val jsonSerializerRegistry = TicketSerializerRegistry

  // Initialize everything
  persistentEntityRegistry.register(
    new TicketEntity(
      rows = config.getInt("ticketing.rows"),
      columns = config.getInt("ticketing.columns")
    )
  )
}

class TicketApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext) =
    new TicketApplication(context) with LagomDevModeComponents with LagomKafkaComponents

  override def loadDevMode(context: LagomApplicationContext) =
    new TicketApplication(context) with LagomDevModeComponents with LagomKafkaComponents

  override def describeService = Some(readDescriptor[TicketService])
}
