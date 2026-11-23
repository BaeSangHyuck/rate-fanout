package dev.sanghyuck.ratefanout.coupon.presentation.restapi

import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunRequestDto
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunResponseDto
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponStatsDto
import dev.sanghyuck.ratefanout.coupon.application.service.CouponIssueService
import dev.sanghyuck.ratefanout.coupon.domain.IssueMode
import dev.sanghyuck.ratefanout.coupon.domain.IssueStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/coupons")
class CouponController(
  private val couponIssueService: CouponIssueService
) {
  @PostMapping("/run")
  suspend fun run(
    @RequestParam(defaultValue = "CONDITIONAL") mode: IssueMode,
    @RequestParam(defaultValue = "300") @Min(1) @Max(1000) requests: Int,
    @RequestParam(defaultValue = "250") @Min(1) @Max(1000) users: Int,
    @RequestParam(defaultValue = "100") @Min(1) @Max(1000) quantity: Int,
    @RequestParam(defaultValue = "5") @Min(0) @Max(20) gapMs: Long,
  ): CouponRunResponseDto {
    requireRedisIfNeeded(mode)
    val request = CouponRunRequestDto(
      mode = mode,
      requests = requests,
      users = users,
      quantity = quantity,
      gapMs = gapMs,
    )
    return couponIssueService.run(request)
  }

  // 부하 테스트용: 쿠폰을 열고, 사용자별로 한 건씩 발급하고, 끝나고 수량을 확인한다
  @PostMapping
  suspend fun open(
    @RequestParam(defaultValue = "100") @Min(1) @Max(100_000) quantity: Int,
  ): Map<String, String> {
    return mapOf("couponId" to couponIssueService.open(quantity))
  }

  @PostMapping("/{couponId}/issue")
  suspend fun issue(
    @PathVariable couponId: String,
    @RequestParam @Min(0) userId: Int,
    @RequestParam(defaultValue = "CONDITIONAL") mode: IssueMode,
    @RequestParam(defaultValue = "5") @Min(0) @Max(20) gapMs: Long,
  ): Map<String, IssueStatus> {
    requireRedisIfNeeded(mode)
    return mapOf("status" to couponIssueService.issueOne(couponId, userId, mode, gapMs))
  }

  @GetMapping("/{couponId}")
  suspend fun stats(@PathVariable couponId: String): CouponStatsDto {
    return couponIssueService.stats(couponId)
  }

  private fun requireRedisIfNeeded(mode: IssueMode) {
    if (mode == IssueMode.REDIS && !couponIssueService.redisReady) {
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis가 연결되지 않은 서버입니다")
    }
  }
}
