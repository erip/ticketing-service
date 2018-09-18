package com.github.erip.ticket.impl
import com.lightbend.lagom.scaladsl.playjson.{ JsonSerializer, JsonSerializerRegistry }

import scala.collection.immutable

object TicketSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: immutable.Seq[JsonSerializer[_]] = TicketEntity.serializers
}
