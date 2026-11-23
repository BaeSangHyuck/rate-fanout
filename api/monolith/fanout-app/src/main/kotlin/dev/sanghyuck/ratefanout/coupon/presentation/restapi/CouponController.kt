package dev.sanghyuck.ratefanout.coupon.presentation.restapi

import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunRequestDto
import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunResponseDto
import dev.sanghyuck.ratefanout.coupon.application.service.CouponIssueService
import dev.sanghyuck.ratefanout.coupon.domain.IssueMode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
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
    if (mode == IssueMode.REDIS && !couponIssueService.redisReady) {
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Redis가 연결되지 않은 서버입니다")
    }
    val request = CouponRunRequestDto(
      mode = mode,
      requests = requests,
      users = users,
      quantity = quantity,
      gapMs = gapMs,
    )
    return couponIssueService.run(request)
  }
}
