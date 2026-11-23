package dev.sanghyuck.ratefanout.coupon.application.adapter.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface CouponJpaRepository : JpaRepository<CouponEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CouponEntity c where c.id = :id")
  fun findForUpdate(id: String): CouponEntity?

  // 확인이랑 차감을 update 한 줄로. 행 락은 이 문장이 도는 동안만 잡힌다
  @Modifying
  @Query("update CouponEntity c set c.remaining = c.remaining - 1 where c.id = :id and c.remaining > 0")
  fun decreaseIfRemaining(id: String): Int
}

interface CouponIssueJpaRepository : JpaRepository<CouponIssueEntity, Long> {
  fun countByCouponId(couponId: String): Long

  @Transactional
  @Modifying
  @Query("delete from CouponIssueEntity i where i.couponId = :couponId")
  fun deleteAllByCouponId(couponId: String): Int
}
