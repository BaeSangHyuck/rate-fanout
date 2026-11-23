package dev.sanghyuck.ratefanout.coupon.application.dto

import dev.sanghyuck.ratefanout.coupon.domain.IssueMode

data class CouponRunRequestDto(
  val mode: IssueMode,
  val requests: Int,
  val users: Int,
  val quantity: Int,
  val gapMs: Long,
)

data class CouponRunResponseDto(
  val options: CouponRunRequestDto,
  val totalMs: Long,
  val issued: Int,
  val overIssued: Int,
  val remaining: Int,
  val soldOut: Int,
  val duplicatesBlocked: Int,
  val p50Ms: Long,
  val p95Ms: Long,
  val maxMs: Long,
)
