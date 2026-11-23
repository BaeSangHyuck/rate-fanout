package dev.sanghyuck.ratefanout.coupon.domain

enum class IssueMode { NONE, PESSIMISTIC, CONDITIONAL, REDIS }

enum class IssueStatus { ISSUED, SOLD_OUT, DUPLICATE }

// 남은 수량이 0이면 더 못 준다. 못 주면 null
data class Coupon(
  val remaining: Int,
) {
  fun issue(): Coupon? {
    if (remaining <= 0) {
      return null
    }
    return Coupon(remaining - 1)
  }
}

// 런타임 예외라 발급 트랜잭션 안에서 던지면 앞의 차감까지 같이 롤백된다
class AlreadyIssuedException(
  couponId: String,
  userId: Int,
) : RuntimeException("already issued. couponId=$couponId, userId=$userId")
