package dev.sanghyuck.ratefanout.order.domain.repository

import dev.sanghyuck.ratefanout.order.domain.Stock

interface StockRepository {
  fun create(productId: String, stock: Stock)

  // forUpdate면 트랜잭션이 끝날 때까지 다른 주문이 이 행을 못 읽는다
  fun find(productId: String, forUpdate: Boolean = false): Stock

  fun save(productId: String, stock: Stock)

  fun delete(productId: String)
}
