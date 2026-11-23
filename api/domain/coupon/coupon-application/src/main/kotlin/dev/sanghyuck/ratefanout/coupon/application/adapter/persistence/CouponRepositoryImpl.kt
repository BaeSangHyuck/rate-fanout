package dev.sanghyuck.ratefanout.coupon.application.adapter.persistence

import dev.sanghyuck.ratefanout.coupon.domain.AlreadyIssuedException
import dev.sanghyuck.ratefanout.coupon.domain.Coupon
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponIssueRepository
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(
  private val couponJpaRepository: CouponJpaRepository
) : CouponRepository {
  override fun create(couponId: String, coupon: Coupon) {
    couponJpaRepository.save(CouponEntity(couponId, coupon.remaining))
  }

  override fun find(couponId: String, forUpdate: Boolean): Coupon {
    val coupon = if (forUpdate) {
      couponJpaRepository.findForUpdate(couponId)
    } else {
      couponJpaRepository.findByIdOrNull(couponId)
    }
    return Coupon(checkNotNull(coupon) { "coupon not found. id=$couponId" }.remaining)
  }

  // 같은 트랜잭션에서 읽은 엔티티 값만 바꾼다. 커밋 때 dirty checking으로 update
  override fun save(couponId: String, coupon: Coupon) {
    couponJpaRepository.getReferenceById(couponId).remaining = coupon.remaining
  }

  override fun decreaseIfRemaining(couponId: String): Boolean {
    return couponJpaRepository.decreaseIfRemaining(couponId) > 0
  }

  override fun delete(couponId: String) {
    couponJpaRepository.deleteById(couponId)
  }
}

@Repository
class CouponIssueRepositoryImpl(
  private val couponIssueJpaRepository: CouponIssueJpaRepository
) : CouponIssueRepository {
  // 바로 flush해서 유니크 제약 위반을 이 자리에서 도메인 예외로 바꾼다
  override fun save(couponId: String, userId: Int) {
    try {
      couponIssueJpaRepository.saveAndFlush(CouponIssueEntity(couponId, userId))
    } catch (e: DataIntegrityViolationException) {
      throw AlreadyIssuedException(couponId, userId)
    }
  }

  override fun count(couponId: String): Int {
    return couponIssueJpaRepository.countByCouponId(couponId).toInt()
  }

  override fun deleteAll(couponId: String) {
    couponIssueJpaRepository.deleteAllByCouponId(couponId)
  }
}
