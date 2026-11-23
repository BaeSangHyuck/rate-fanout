package dev.sanghyuck.ratefanout.order.application.service

import dev.sanghyuck.ratefanout.common.blockingIo
import dev.sanghyuck.ratefanout.common.logger
import dev.sanghyuck.ratefanout.order.application.dto.OrderRunRequestDto
import dev.sanghyuck.ratefanout.order.application.dto.OrderRunResponseDto
import dev.sanghyuck.ratefanout.order.application.dto.OrderTraceDto
import dev.sanghyuck.ratefanout.order.domain.OrderStatus
import dev.sanghyuck.ratefanout.order.domain.Stock
import dev.sanghyuck.ratefanout.order.domain.repository.StockRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.random.Random

@Service
class OrderSimulationService(
  private val stockRepository: StockRepository,
  private val stockCommandService: StockCommandService,
  private val orderEventOutbox: OrderEventOutbox,
) {
  private val log = logger()

  suspend fun run(request: OrderRunRequestDto): OrderRunResponseDto {
    // 실행마다 상품을 새로 만들고 끝나면 지운다. 여러 명이 동시에 눌러도 섞이지 않게
    val productId = UUID.randomUUID().toString()
    blockingIo { stockRepository.create(productId, Stock(request.stock)) }

    try {
      val startedAt = System.nanoTime()
      val elapsedMs = { (System.nanoTime() - startedAt) / 1_000_000 }

      val traces = coroutineScope {
        (1..request.orders).map { orderNo ->
          async { order(productId, orderNo, request, elapsedMs) }
        }.awaitAll()
      }
      val totalMs = elapsedMs()

      val finalStock = blockingIo { stockRepository.find(productId).quantity }
      val paid = traces.count { it.status == OrderStatus.PAID }
      val expectedStock = request.stock - paid
      val outbox = orderEventOutbox.relay(productId, request.publishFailPct, request.seed)

      log.info("[OrderRun] mode={}, paid={}, stock={}, expected={}", request.mode, paid, finalStock, expectedStock)

      return OrderRunResponseDto(
        options = request,
        totalMs = totalMs,
        finalStock = finalStock,
        expectedStock = expectedStock,
        consistent = finalStock == expectedStock,
        paid = paid,
        compensated = traces.count { it.status == OrderStatus.COMPENSATED },
        soldOut = traces.count { it.status == OrderStatus.SOLD_OUT },
        orders = traces,
        outbox = outbox,
      )
    } finally {
      blockingIo {
        orderEventOutbox.deleteAll(productId)
        stockRepository.delete(productId)
      }
    }
  }

  private suspend fun order(
    productId: String,
    orderNo: Int,
    request: OrderRunRequestDto,
    elapsedMs: () -> Long,
  ): OrderTraceDto {
    val startMs = elapsedMs()

    val decreased = blockingIo {
      stockCommandService.decrease(productId, orderNo, request.mode, request.gapMs)
    }
    if (!decreased) {
      return OrderTraceDto(orderNo, OrderStatus.SOLD_OUT, startMs, elapsedMs())
    }

    // 결제 승인은 트랜잭션 밖에서 기다린다. 승인 대기 동안 락이랑 커넥션을 잡고 있지 않게
    val payment = Random(request.seed * 1_000 + orderNo)
    delay(payment.nextLong(150, 400))
    val approved = payment.nextInt(100) >= request.paymentFailPct

    if (!approved) {
      // 차감은 이미 커밋됐으니 롤백이 아니라 반대로 더해서 되돌린다(보상 트랜잭션)
      blockingIo { stockCommandService.restore(productId, orderNo, request.mode, request.gapMs) }
      return OrderTraceDto(orderNo, OrderStatus.COMPENSATED, startMs, elapsedMs())
    }

    return OrderTraceDto(orderNo, OrderStatus.PAID, startMs, elapsedMs())
  }
}
