package dev.sanghyuck.ratefanout.order.application.service

import dev.sanghyuck.ratefanout.order.domain.LockMode
import dev.sanghyuck.ratefanout.order.domain.Stock
import dev.sanghyuck.ratefanout.order.domain.StockEventType
import dev.sanghyuck.ratefanout.order.domain.repository.OutboxRepository
import dev.sanghyuck.ratefanout.order.domain.repository.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockCommandService(
  private val stockRepository: StockRepository,
  private val outboxRepository: OutboxRepository,
) {
  @Transactional
  fun decrease(productId: String, orderNo: Int, lockMode: LockMode, gapMs: Long): Boolean {
    return change(productId, orderNo, lockMode, gapMs, StockEventType.STOCK_DECREASED) { it.decrease() }
  }

  @Transactional
  fun restore(productId: String, orderNo: Int, lockMode: LockMode, gapMs: Long): Boolean {
    return change(productId, orderNo, lockMode, gapMs, StockEventType.STOCK_RESTORED) { it.restore() }
  }

  // 읽고 → 계산하고 → 저장. 락이 없으면 그 사이에 들어온 다른 주문의 차감을 덮어쓴다(lost update)
  private fun change(
    productId: String,
    orderNo: Int,
    lockMode: LockMode,
    gapMs: Long,
    eventType: StockEventType,
    next: (Stock) -> Stock?,
  ): Boolean {
    val stock = stockRepository.find(productId, forUpdate = lockMode == LockMode.PESSIMISTIC)

    Thread.sleep(gapMs) // 조회~저장 사이 처리 시간. 경합이 잘 보이도록 일부러 둔다

    val changed = next(stock) ?: return false
    stockRepository.save(productId, changed)

    // 재고 변경이랑 같은 트랜잭션에 이벤트를 남겨야 둘 중 하나만 반영되는 일이 없다
    outboxRepository.append(productId, eventType, orderNo)
    return true
  }
}
