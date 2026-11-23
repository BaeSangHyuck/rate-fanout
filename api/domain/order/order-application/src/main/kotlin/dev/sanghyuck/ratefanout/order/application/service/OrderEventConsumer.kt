package dev.sanghyuck.ratefanout.order.application.service

import dev.sanghyuck.ratefanout.order.domain.repository.ProcessedEventRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// 재고 이벤트를 받는 쪽. 같은 eventId가 또 와도 processed_event PK에 걸러져 한 번만 처리된다
@Component
class OrderEventConsumer(
  private val processedEventRepository: ProcessedEventRepository,
) {
  // shortcut: 실행별 수신 통계를 메모리에 둔다. 인스턴스가 여러 대가 되면 DB 집계로 바꿔야 한다
  private val received = ConcurrentHashMap<String, ReceivedStats>()

  fun consume(eventId: String, runId: String) {
    val stats = received.computeIfAbsent(runId) { ReceivedStats() }
    if (processedEventRepository.markProcessed(eventId, runId)) {
      stats.consumed.incrementAndGet()
    } else {
      stats.duplicates.incrementAndGet()
    }
  }

  // 카프카로 보내면 비동기라, 보낸 만큼 다 받을 때까지 기다렸다가 결과를 낸다
  suspend fun awaitReceived(runId: String, delivered: Int): ReceivedStats {
    withTimeoutOrNull(RECEIVE_TIMEOUT_MS) {
      while ((received[runId]?.total() ?: 0) < delivered) delay(50)
    }
    return received[runId] ?: ReceivedStats()
  }

  fun forget(runId: String) {
    received.remove(runId)
  }

  class ReceivedStats {
    val consumed = AtomicInteger()
    val duplicates = AtomicInteger()

    fun total() = consumed.get() + duplicates.get()
  }

  companion object {
    const val RECEIVE_TIMEOUT_MS = 10_000L
  }
}
