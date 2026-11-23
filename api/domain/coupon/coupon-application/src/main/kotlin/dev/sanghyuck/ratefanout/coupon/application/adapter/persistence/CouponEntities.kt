package dev.sanghyuck.ratefanout.coupon.application.adapter.persistence

import dev.sanghyuck.ratefanout.common.jpa.AssignedIdEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "coupon")
class CouponEntity(
  @Id
  private var id: String,
  var remaining: Int,
) : AssignedIdEntity() {
  override fun getId(): String = id
}

// (쿠폰, 사용자) 유니크 제약이 중복 발급을 막는 마지막 방어선
@Entity
@Table(
  name = "coupon_issue",
  uniqueConstraints = [UniqueConstraint(name = "uk_coupon_issue_user", columnNames = ["coupon_id", "user_id"])],
)
class CouponIssueEntity(
  var couponId: String,
  var userId: Int,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null
}
