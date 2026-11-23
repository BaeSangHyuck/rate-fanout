package dev.sanghyuck.ratefanout.coupon.domain.repository

import dev.sanghyuck.ratefanout.coupon.domain.Coupon

interface CouponRepository {
  fun create(couponId: String, coupon: Coupon)

  fun find(couponId: String, forUpdate: Boolean = false): Coupon

  fun save(couponId: String, coupon: Coupon)

  // 확인이랑 차감을 한 번에. 남아 있을 때만 줄고 true
  fun decreaseIfRemaining(couponId: String): Boolean

  fun delete(couponId: String)
}

interface CouponIssueRepository {
  // 같은 사용자가 또 받으려 하면 AlreadyIssuedException
  fun save(couponId: String, userId: Int)

  fun count(couponId: String): Int

  fun deleteAll(couponId: String)
}
