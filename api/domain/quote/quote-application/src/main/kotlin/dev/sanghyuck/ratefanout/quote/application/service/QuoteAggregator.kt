package dev.sanghyuck.ratefanout.quote.application.service

import dev.sanghyuck.ratefanout.quote.application.dto.QuoteRequestDto
import dev.sanghyuck.ratefanout.quote.application.dto.QuoteResponseDto
import dev.sanghyuck.ratefanout.quote.domain.Attempt
import dev.sanghyuck.ratefanout.quote.domain.INSTITUTIONS
import dev.sanghyuck.ratefanout.quote.domain.Institution
import dev.sanghyuck.ratefanout.quote.domain.InstitutionResult
import dev.sanghyuck.ratefanout.quote.domain.Outcome
import dev.sanghyuck.ratefanout.quote.domain.Strategy
import dev.sanghyuck.ratefanout.quote.domain.bestOffers
import dev.sanghyuck.ratefanout.quote.domain.port.InstitutionClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Service
import kotlin.time.TimeSource

@Service
class QuoteAggregator(
  private val institutionClient: InstitutionClient
) {
  // 테스트에서 가상 시간을 넣으려고 열어 뒀다
  var timeSource: TimeSource = TimeSource.Monotonic

  suspend fun aggregate(
    request: QuoteRequestDto,
    scenario: Long,
    institutions: List<Institution> = INSTITUTIONS,
  ): QuoteResponseDto {
    val startedAt = timeSource.markNow()
    val elapsedMs = { startedAt.elapsedNow().inWholeMilliseconds }

    val results = when (request.strategy) {
      Strategy.SEQUENTIAL -> institutions.map { fetchQuote(it, request, scenario, elapsedMs) }
      Strategy.PARALLEL -> coroutineScope {
        // 한 번에 나가는 호출 수 제한. 기관 쪽 rate limit이나 커넥션 풀을 넘기지 않게
        val semaphore = Semaphore(request.concurrency)
        institutions.map { institution ->
          async { semaphore.withPermit { fetchQuote(institution, request, scenario, elapsedMs) } }
        }.awaitAll()
      }
    }

    val offers = results.bestOffers()

    return QuoteResponseDto(
      options = request,
      totalMs = elapsedMs(),
      succeeded = offers.size,
      failed = results.size - offers.size,
      offers = offers,
      results = results,
    )
  }

  // 에러만 재시도하고 타임아웃은 재시도 안 한다. 이미 느린 곳을 또 기다리면 전체 응답이 그만큼 늦어진다
  // 실패는 예외로 안 던지고 결과에 담는다. 한 곳이 죽어도 나머지 결과는 나가야 해서
  private suspend fun fetchQuote(
    institution: Institution,
    request: QuoteRequestDto,
    scenario: Long,
    elapsedMs: () -> Long,
  ): InstitutionResult {
    val attempts = mutableListOf<Attempt>()

    for (attempt in 0..request.retries) {
      val startMs = elapsedMs()
      try {
        val quote = withTimeout(request.timeoutMs) {
          institutionClient.fetch(institution.code, scenario, attempt)
        }
        attempts += Attempt(startMs, elapsedMs(), Outcome.OK)
        return InstitutionResult(institution.code, institution.name, Outcome.OK, quote, attempts)
      } catch (e: TimeoutCancellationException) {
        attempts += Attempt(startMs, elapsedMs(), Outcome.TIMEOUT)
        return InstitutionResult(institution.code, institution.name, Outcome.TIMEOUT, null, attempts)
      } catch (e: CancellationException) {
        throw e // 요청 자체가 취소된 건 삼키면 안 된다
      } catch (e: Exception) {
        attempts += Attempt(startMs, elapsedMs(), Outcome.ERROR)
      }
    }

    return InstitutionResult(institution.code, institution.name, Outcome.ERROR, null, attempts)
  }
}
