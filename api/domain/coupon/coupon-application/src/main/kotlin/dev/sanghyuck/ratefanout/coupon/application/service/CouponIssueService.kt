package dev.sanghyuck.ratefanout.coupon.application.service

import dev.sanghyuck.ratefanout.common.blockingIo
import dev.sanghyuck.ratefanout.common.logger
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunRequestDto
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunResponseDto
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponStatsDto
import dev.sanghyuck.ratefanout.coupon.domain.AlreadyIssuedException
import dev.sanghyuck.ratefanout.coupon.domain.Coupon
import dev.sanghyuck.ratefanout.coupon.domain.IssueMode
import dev.sanghyuck.ratefanout.coupon.domain.IssueStatus
import dev.sanghyuck.ratefanout.coupon.domain.port.CouponIssueGate
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponIssueRepository
import dev.sanghyuck.ratefanout.coupon.domain.repository.CouponRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CouponIssueService(
  private val couponRepository: CouponRepository,
  private val couponIssueRepository: CouponIssueRepository,
  private val couponCommandService: CouponCommandService,
  // 레디스 주소가 없으면 빈이 없어 null. 그때는 REDIS 모드를 못 쓴다
  private val couponIssueGate: CouponIssueGate?,
) {
  private val log = logger()

  val redisReady: Boolean
    get() = couponIssueGate != null

  suspend fun run(request: CouponRunRequestDto): CouponRunResponseDto {
    val couponId = UUID.randomUUID().toString()
    val gate = if (request.mode == IssueMode.REDIS) checkNotNull(couponIssueGate) { "redis is not configured" } else null
    blockingIo {
      couponRepository.create(couponId, Coupon(request.quantity))
      gate?.open(couponId, request.quantity)
    }

    try {
      val startedAt = System.nanoTime()
      val elapsedMs = { (System.nanoTime() - startedAt) / 1_000_000 }

      val results = coroutineScope {
        (0 until request.requests).map { index ->
          // 사용자 수가 요청 수보다 적으면 옆 요청끼리 같은 사용자가 된다(버튼 연타)
          val userId = (index.toLong() * request.users / request.requests).toInt()
          async {
            val status = blockingIo { issue(couponId, userId, request.mode, request.gapMs, gate) }
            status to elapsedMs()
          }
        }.awaitAll()
      }
      val totalMs = elapsedMs()

      val issued = blockingIo { couponIssueRepository.count(couponId) }
      val remaining = blockingIo { couponRepository.find(couponId).remaining }
      val latencies = results.map { it.second }.sorted()

      log.info("[CouponRun] mode={}, issued={}/{}, p95={}ms", request.mode, issued, request.quantity, percentile(latencies, 95))

      return CouponRunResponseDto(
        options = request,
        totalMs = totalMs,
        issued = issued,
        overIssued = maxOf(0, issued - request.quantity),
        remaining = remaining,
        soldOut = results.count { it.first == IssueStatus.SOLD_OUT },
        duplicatesBlocked = results.count { it.first == IssueStatus.DUPLICATE },
        p50Ms = percentile(latencies, 50),
        p95Ms = percentile(latencies, 95),
        maxMs = latencies.last(),
      )
    } finally {
      blockingIo {
        gate?.close(couponId)
        couponIssueRepository.deleteAll(couponId)
        couponRepository.delete(couponId)
      }
    }
  }

  // 아래 세 개는 부하 테스트용. 사용자 한 명의 요청 하나가 HTTP 요청 하나가 되도록 쿠폰을 열어 두고 따로 발급한다
  suspend fun open(quantity: Int): String {
    val couponId = UUID.randomUUID().toString()
    blockingIo {
      couponRepository.create(couponId, Coupon(quantity))
      couponIssueGate?.open(couponId, quantity)
    }
    return couponId
  }

  suspend fun issueOne(couponId: String, userId: Int, mode: IssueMode, gapMs: Long): IssueStatus {
    val gate = if (mode == IssueMode.REDIS) checkNotNull(couponIssueGate) { "redis is not configured" } else null
    return blockingIo { issue(couponId, userId, mode, gapMs, gate) }
  }

  suspend fun stats(couponId: String): CouponStatsDto {
    return blockingIo {
      CouponStatsDto(issued = couponIssueRepository.count(couponId), remaining = couponRepository.find(couponId).remaining)
    }
  }

  private fun issue(couponId: String, userId: Int, mode: IssueMode, gapMs: Long, gate: CouponIssueGate?): IssueStatus {
    // 레디스에서 떨어진 요청은 DB 커넥션도 안 잡고 바로 돌아간다
    val passed = gate?.tryAcquire(couponId, userId) ?: IssueStatus.ISSUED
    if (passed != IssueStatus.ISSUED) {
      return passed
    }

    return try {
      couponCommandService.issue(couponId, userId, mode, gapMs)
    } catch (e: AlreadyIssuedException) {
      IssueStatus.DUPLICATE
    }
  }

  private fun percentile(sorted: List<Long>, p: Int): Long {
    return sorted[((sorted.size - 1) * p / 100.0).toInt()]
  }
}
