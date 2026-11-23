package dev.sanghyuck.ratefanout.coupon.application.service

import dev.sanghyuck.ratefanout.coupon.domain.IssueMode
import dev.sanghyuck.ratefanout.coupon.domain.IssueStatus
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponIssueRepository
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponCommandService(
  private val couponRepository: CouponRepository,
  private val couponIssueRepository: CouponIssueRepository,
) {
  // 같은 사용자면 AlreadyIssuedException이 나고, 롤백되면서 앞의 차감도 같이 취소된다
  @Transactional
  fun issue(couponId: String, userId: Int, mode: IssueMode, gapMs: Long): IssueStatus {
    val decreased = when (mode) {
      IssueMode.NONE, IssueMode.PESSIMISTIC -> decreaseAfterRead(couponId, mode, gapMs)
      // REDIS는 앞단에서 이미 걸러졌고, DB 차감은 조건부로 한 번 더 지킨다
      IssueMode.CONDITIONAL, IssueMode.REDIS -> decreaseIfRemaining(couponId, gapMs)
    }
    if (!decreased) {
      return IssueStatus.SOLD_OUT
    }

    couponIssueRepository.save(couponId, userId)
    return IssueStatus.ISSUED
  }

  private fun decreaseAfterRead(couponId: String, mode: IssueMode, gapMs: Long): Boolean {
    val coupon = couponRepository.find(couponId, forUpdate = mode == IssueMode.PESSIMISTIC)

    Thread.sleep(gapMs) // 발급 자격 확인 같은 처리 시간. 비관적 락이면 이 동안 락을 쥐고 있다

    val issued = coupon.issue() ?: return false
    couponRepository.save(couponId, issued)
    return true
  }

  private fun decreaseIfRemaining(couponId: String, gapMs: Long): Boolean {
    Thread.sleep(gapMs) // 같은 처리 시간을 락 밖에서 쓴다
    return couponRepository.decreaseIfRemaining(couponId)
  }
}
