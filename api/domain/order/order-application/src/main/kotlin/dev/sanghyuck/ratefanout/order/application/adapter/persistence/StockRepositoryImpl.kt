package dev.sanghyuck.ratefanout.order.application.adapter.persistence

import dev.sanghyuck.ratefanout.order.domain.Stock
import dev.sanghyuck.ratefanout.order.domain.repository.StockRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
  private val productJpaRepository: ProductJpaRepository
) : StockRepository {
  override fun create(productId: String, stock: Stock) {
    productJpaRepository.save(ProductEntity(productId, stock.quantity))
  }

  override fun find(productId: String, forUpdate: Boolean): Stock {
    val product = if (forUpdate) {
      productJpaRepository.findForUpdate(productId)
    } else {
      productJpaRepository.findByIdOrNull(productId)
    }
    return Stock(checkNotNull(product) { "product not found. id=$productId" }.stock)
  }

  // 같은 트랜잭션에서 읽은 엔티티라 1차 캐시에서 꺼낸다. 값만 바꾸면 커밋 때 update가 나간다(dirty checking)
  override fun save(productId: String, stock: Stock) {
    productJpaRepository.getReferenceById(productId).stock = stock.quantity
  }

  override fun delete(productId: String) {
    productJpaRepository.deleteById(productId)
  }
}
