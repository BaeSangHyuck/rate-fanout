package dev.sanghyuck.ratefanout.order.application.adapter.persistence

import dev.sanghyuck.ratefanout.common.jpa.AssignedIdEntity
import dev.sanghyuck.ratefanout.order.domain.StockEventType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product")
class ProductEntity(
  @Id
  private var id: String,
  var stock: Int,
) : AssignedIdEntity() {
  override fun getId(): String = id
}

@Entity
@Table(name = "outbox")
class OutboxEventEntity(
  @Id
  private var eventId: String,
  var runId: String,
  @Enumerated(EnumType.STRING)
  var type: StockEventType,
  var orderNo: Int,
  var published: Boolean = false,
) : AssignedIdEntity() {
  override fun getId(): String = eventId
}

@Entity
@Table(name = "processed_event")
class ProcessedEventEntity(
  @Id
  private var eventId: String,
  var runId: String,
) : AssignedIdEntity() {
  override fun getId(): String = eventId
}
