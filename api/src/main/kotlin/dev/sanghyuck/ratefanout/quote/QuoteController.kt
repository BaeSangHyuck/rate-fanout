package dev.sanghyuck.ratefanout.quote

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 파라미터 제약은 Spring 내장 메서드 검증이 처리해 400을 돌려준다(@Validated AOP를 쓰면 500)
@RestController
@RequestMapping("/api/quotes")
class QuoteController(private val aggregator: QuoteAggregator) {

    @GetMapping
    suspend fun quotes(
        @RequestParam(defaultValue = "PARALLEL") strategy: Strategy,
        @RequestParam(defaultValue = "2000") @Min(100) @Max(10000) timeoutMs: Long,
        @RequestParam(defaultValue = "1") @Min(0) @Max(2) retries: Int,
        @RequestParam(defaultValue = "12") @Min(1) @Max(12) concurrency: Int,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) scenario: Long,
    ): AggregateResponse =
        aggregator.aggregate(QuoteOptions(strategy, timeoutMs, retries, concurrency), scenario)
}
