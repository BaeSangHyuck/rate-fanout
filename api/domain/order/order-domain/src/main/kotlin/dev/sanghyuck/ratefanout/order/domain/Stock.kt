package dev.sanghyuck.ratefanout.order.domain

enum class LockMode { NONE, PESSIMISTIC }

enum class OrderStatus { PAID, COMPENSATED, SOLD_OUT }

enum class StockEventType { STOCK_DECREASED, STOCK_RESTORED }

// 재고는 0 밑으로 못 내려간다. 못 빼면 null
data class Stock(
  val quantity: Int,
) {
  fun decrease(): Stock? {
    if (quantity <= 0) {
      return null
    }
    return Stock(quantity - 1)
  }

  fun restore(): Stock {
    return Stock(quantity + 1)
  }
}
