package dev.sanghyuck.ratefanout.order.application.service

import dev.sanghyuck.ratefanout.common.blockingIo
import dev.sanghyuck.ratefanout.common.logger
import dev.sanghyuck.ratefanout.order.application.dto.OutboxStatsDto
import dev.sanghyuck.ratefanout.order.domain.port.OrderEventPublisher
import dev.sanghyuck.ratefanout.order.domain.repository.OutboxRepository
import dev.sanghyuck.ratefanout.order.domain.repository.ProcessedEventRepository
import org.springframework.stereotype.Component
import kotlin.random.Random

// 실서비스면 스케줄러로 주기적으로 돌 부분인데, 데모라 주문이 끝난 뒤 같은 요청 안에서 돌린다
@Component
class OrderEventOutbox(
  private val outboxRepository: OutboxRepository,
  private val processedEventRepository: ProcessedEventRepository,
  private val orderEventPublisher: OrderEventPublisher,
  private val orderEventConsumer: OrderEventConsumer,
) {
  private val log = logger()

  suspend fun relay(runId: String, publishFailPct: Int, seed: Long): OutboxStatsDto {
    val random = Random(seed)
    val events = blockingIo { outboxRepository.count(runId) }
    var delivered = 0
    var publishFailures = 0
    var lostAcks = 0
    var rounds = 0

    var pending = blockingIo { outboxRepository.findPendingIds(runId) }
    while (pending.isNotEmpty() && rounds < MAX_RELAY_ROUNDS) {
      rounds++
      for (eventId in pending) {
        val dice = random.nextInt(100)

        // 전달 실패. published를 안 찍었으니 다음 라운드에 다시 보낸다
        if (dice < publishFailPct / 2) {
          publishFailures++
          continue
        }

        try {
          blockingIo { orderEventPublisher.publish(eventId, runId) }
        } catch (e: Exception) {
          log.warn("[OutboxRelay] publish failed. eventId={}, msg={}", eventId, e.message)
          publishFailures++
          continue
        }
        delivered++

        // 컨슈머는 받았는데 응답이 유실된 경우. 다음 라운드에 같은 이벤트가 또 나간다
        if (dice < publishFailPct) {
          lostAcks++
          continue
        }

        blockingIo { outboxRepository.markPublished(eventId) }
      }
      pending = blockingIo { outboxRepository.findPendingIds(runId) }
    }

    val received = orderEventConsumer.awaitReceived(runId, delivered)

    return OutboxStatsDto(
      events = events,
      consumed = received.consumed.get(),
      duplicatesIgnored = received.duplicates.get(),
      publishFailures = publishFailures,
      lostAcks = lostAcks,
      rounds = rounds,
      pending = pending.size,
    )
  }

  fun deleteAll(runId: String) {
    orderEventConsumer.forget(runId)
    processedEventRepository.deleteAll(runId)
    outboxRepository.deleteAll(runId)
  }

  companion object {
    const val MAX_RELAY_ROUNDS = 50
  }
}
