package dev.sanghyuck.ratefanout.order.application.adapter.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface ProductJpaRepository : JpaRepository<ProductEntity, String> {
  // select ... for update. 트랜잭션이 끝날 때까지 다른 주문은 이 행에서 기다린다
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ProductEntity p where p.id = :id")
  fun findForUpdate(id: String): ProductEntity?
}

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, String> {
  fun countByRunId(runId: String): Long

  @Query("select o.eventId from OutboxEventEntity o where o.runId = :runId and o.published = false")
  fun findPendingIds(runId: String): List<String>

  @Transactional
  @Modifying
  @Query("update OutboxEventEntity o set o.published = true where o.eventId = :eventId")
  fun markPublished(eventId: String): Int

  @Transactional
  @Modifying
  @Query("delete from OutboxEventEntity o where o.runId = :runId")
  fun deleteAllByRunId(runId: String): Int
}

interface ProcessedEventJpaRepository : JpaRepository<ProcessedEventEntity, String> {
  @Transactional
  @Modifying
  @Query("delete from ProcessedEventEntity e where e.runId = :runId")
  fun deleteAllByRunId(runId: String): Int
}
