package dev.sanghyuck.ratefanout.quote.presentation.restapi

import dev.sanghyuck.ratefanout.quote.application.dto.QuoteRequestDto
import dev.sanghyuck.ratefanout.quote.application.dto.QuoteResponseDto
import dev.sanghyuck.ratefanout.quote.application.service.QuoteAggregator
import dev.sanghyuck.ratefanout.quote.domain.Strategy
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// @Validated를 붙이면 제약 위반이 500으로 나가서, 스프링 내장 메서드 검증(400)에 맡긴다
@RestController
@RequestMapping("/api/quotes")
class QuoteController(
  private val quoteAggregator: QuoteAggregator
) {
  @GetMapping
  suspend fun getQuotes(
    @RequestParam(defaultValue = "PARALLEL") strategy: Strategy,
    @RequestParam(defaultValue = "2000") @Min(100) @Max(10000) timeoutMs: Long,
    @RequestParam(defaultValue = "1") @Min(0) @Max(2) retries: Int,
    @RequestParam(defaultValue = "12") @Min(1) @Max(12) concurrency: Int,
    @RequestParam(defaultValue = "1") @Min(1) @Max(1_000_000) scenario: Long,
  ): QuoteResponseDto {
    val request = QuoteRequestDto(
      strategy = strategy,
      timeoutMs = timeoutMs,
      retries = retries,
      concurrency = concurrency,
    )
    return quoteAggregator.aggregate(request, scenario)
  }
}
