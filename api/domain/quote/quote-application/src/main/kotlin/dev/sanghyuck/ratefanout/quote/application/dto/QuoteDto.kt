package dev.sanghyuck.ratefanout.quote.application.dto

import dev.sanghyuck.ratefanout.quote.domain.InstitutionResult
import dev.sanghyuck.ratefanout.quote.domain.Strategy

data class QuoteRequestDto(
  val strategy: Strategy,
  val timeoutMs: Long,
  val retries: Int,
  val concurrency: Int,
)

data class QuoteResponseDto(
  val options: QuoteRequestDto,
  val totalMs: Long,
  val succeeded: Int,
  val failed: Int,
  val offers: List<InstitutionResult>, // 성공한 곳만, 금리 낮은 순
  val results: List<InstitutionResult>,
)
