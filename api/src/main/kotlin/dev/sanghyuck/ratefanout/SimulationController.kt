package dev.sanghyuck.ratefanout

import dev.sanghyuck.ratefanout.coupon.CouponOptions
import dev.sanghyuck.ratefanout.coupon.CouponRunResponse
import dev.sanghyuck.ratefanout.coupon.CouponSimulator
import dev.sanghyuck.ratefanout.coupon.IssueMode
import dev.sanghyuck.ratefanout.order.LockMode
import dev.sanghyuck.ratefanout.order.OrderOptions
import dev.sanghyuck.ratefanout.order.OrderRunResponse
import dev.sanghyuck.ratefanout.order.OrderSimulator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 실행마다 상품·쿠폰 행을 새로 만들고 끝나면 지운다. 상한은 무료 서버에서 한 번 실행이 수십 초를 넘지 않게 잡았다
@RestController
class SimulationController(private val orders: OrderSimulator, private val coupons: CouponSimulator) {

    @PostMapping("/api/orders/run")
    suspend fun orders(
        @RequestParam(defaultValue = "PESSIMISTIC") mode: LockMode,
        @RequestParam(defaultValue = "20") @Min(1) @Max(60) orders: Int,
        @RequestParam(defaultValue = "10") @Min(1) @Max(60) stock: Int,
        @RequestParam(defaultValue = "20") @Min(0) @Max(50) paymentFailPct: Int,
        @RequestParam(defaultValue = "25") @Min(0) @Max(50) publishFailPct: Int,
        @RequestParam(defaultValue = "30") @Min(0) @Max(100) gapMs: Long,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) seed: Long,
    ): OrderRunResponse = this.orders.run(OrderOptions(mode, orders, stock, paymentFailPct, publishFailPct, gapMs, seed))

    @PostMapping("/api/coupons/run")
    suspend fun coupons(
        @RequestParam(defaultValue = "CONDITIONAL") mode: IssueMode,
        @RequestParam(defaultValue = "300") @Min(1) @Max(1000) requests: Int,
        @RequestParam(defaultValue = "250") @Min(1) @Max(1000) users: Int,
        @RequestParam(defaultValue = "100") @Min(1) @Max(1000) quantity: Int,
        @RequestParam(defaultValue = "5") @Min(0) @Max(20) gapMs: Long,
    ): CouponRunResponse = coupons.run(CouponOptions(mode, requests, users, quantity, gapMs))
}
