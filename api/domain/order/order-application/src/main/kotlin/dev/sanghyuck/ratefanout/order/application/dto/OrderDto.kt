package dev.sanghyuck.ratefanout.order.application.dto

import dev.sanghyuck.ratefanout.order.domain.LockMode
import dev.sanghyuck.ratefanout.order.domain.OrderStatus

data class OrderRunRequestDto(
  val mode: LockMode,
  val orders: Int,
  val stock: Int,
  val paymentFailPct: Int,
  val publishFailPct: Int,
  val gapMs: Long,
  val seed: Long,
)

data class OrderTraceDto(
  val no: Int,
  val status: OrderStatus,
  val startMs: Long,
  val endMs: Long,
)

data class OutboxStatsDto(
  val events: Int,
  val consumed: Int,
  val duplicatesIgnored: Int,
  val publishFailures: Int,
  val lostAcks: Int,
  val rounds: Int,
  val pending: Int,
)

data class OrderRunResponseDto(
  val options: OrderRunRequestDto,
  val totalMs: Long,
  val finalStock: Int,
  val expectedStock: Int,
  val consistent: Boolean,
  val paid: Int,
  val compensated: Int,
  val soldOut: Int,
  val orders: List<OrderTraceDto>,
  val outbox: OutboxStatsDto,
)
