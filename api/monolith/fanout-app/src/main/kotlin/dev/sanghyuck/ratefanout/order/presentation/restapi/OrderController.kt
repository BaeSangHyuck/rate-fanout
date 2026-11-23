package dev.sanghyuck.ratefanout.order.presentation.restapi

import dev.sanghyuck.ratefanout.order.application.dto.OrderRunRequestDto
import dev.sanghyuck.ratefanout.order.application.dto.OrderRunResponseDto
import dev.sanghyuck.ratefanout.order.application.service.OrderSimulationService
import dev.sanghyuck.ratefanout.order.domain.LockMode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
  private val orderSimulationService: OrderSimulationService
) {
  // 상한은 무료 서버에서 한 번 실행이 수십 초를 넘지 않게 잡았다
  @PostMapping("/run")
  suspend fun run(
    @RequestParam(defaultValue = "PESSIMISTIC") mode: LockMode,
    @RequestParam(defaultValue = "20") @Min(1) @Max(60) orders: Int,
    @RequestParam(defaultValue = "10") @Min(1) @Max(60) stock: Int,
    @RequestParam(defaultValue = "20") @Min(0) @Max(50) paymentFailPct: Int,
    @RequestParam(defaultValue = "25") @Min(0) @Max(50) publishFailPct: Int,
    @RequestParam(defaultValue = "30") @Min(0) @Max(100) gapMs: Long,
    @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) seed: Long,
  ): OrderRunResponseDto {
    val request = OrderRunRequestDto(
      mode = mode,
      orders = orders,
      stock = stock,
      paymentFailPct = paymentFailPct,
      publishFailPct = publishFailPct,
      gapMs = gapMs,
      seed = seed,
    )
    return orderSimulationService.run(request)
  }
}
