package dev.sanghyuck.ratefanout.order.application.adapter.persistence

import dev.sanghyuck.ratefanout.order.domain.StockEventType
import dev.sanghyuck.ratefanout.order.domain.repository.OutboxRepository
import dev.sanghyuck.ratefanout.order.domain.repository.ProcessedEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class OutboxRepositoryImpl(
  private val outboxEventJpaRepository: OutboxEventJpaRepository
) : OutboxRepository {
  override fun append(runId: String, type: StockEventType, orderNo: Int) {
    outboxEventJpaRepository.save(OutboxEventEntity(UUID.randomUUID().toString(), runId, type, orderNo))
  }

  override fun count(runId: String): Int {
    return outboxEventJpaRepository.countByRunId(runId).toInt()
  }

  override fun findPendingIds(runId: String): List<String> {
    return outboxEventJpaRepository.findPendingIds(runId)
  }

  override fun markPublished(eventId: String) {
    outboxEventJpaRepository.markPublished(eventId)
  }

  override fun deleteAll(runId: String) {
    outboxEventJpaRepository.deleteAllByRunId(runId)
  }
}

@Repository
class ProcessedEventRepositoryImpl(
  private val processedEventJpaRepository: ProcessedEventJpaRepository
) : ProcessedEventRepository {
  // event_id가 PK라 같은 이벤트가 두 번 들어오면 flush 시점에 PK 중복으로 걸린다
  override fun markProcessed(eventId: String, runId: String): Boolean {
    return try {
      processedEventJpaRepository.saveAndFlush(ProcessedEventEntity(eventId, runId))
      true
    } catch (e: DataIntegrityViolationException) {
      false
    }
  }

  override fun deleteAll(runId: String) {
    processedEventJpaRepository.deleteAllByRunId(runId)
  }
}
