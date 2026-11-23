package dev.sanghyuck.ratefanout.order.domain.repository

import dev.sanghyuck.ratefanout.order.domain.StockEventType

interface OutboxRepository {
  fun append(runId: String, type: StockEventType, orderNo: Int)

  fun count(runId: String): Int

  fun findPendingIds(runId: String): List<String>

  fun markPublished(eventId: String)

  fun deleteAll(runId: String)
}

// 컨슈머 쪽 처리 기록
interface ProcessedEventRepository {
  // 이미 처리한 이벤트면 false
  fun markProcessed(eventId: String, runId: String): Boolean

  fun deleteAll(runId: String)
}
