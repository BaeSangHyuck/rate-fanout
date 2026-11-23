package dev.sanghyuck.ratefanout.coupon.domain.port

import dev.sanghyuck.ratefanout.coupon.domain.IssueStatus

// DB 앞에서 수량과 중복 사용자를 먼저 거른다. 통과한 요청만 DB 트랜잭션까지 간다
interface CouponIssueGate {
  fun open(couponId: String, quantity: Int)

  // 통과면 ISSUED, 아니면 SOLD_OUT 또는 DUPLICATE
  fun tryAcquire(couponId: String, userId: Int): IssueStatus

  fun close(couponId: String)
}
