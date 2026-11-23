package dev.sanghyuck.ratefanout.quote

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
    private val client: InstitutionClient,
) {
    // 테스트에서 가상 시간을 넣을 수 있게 열어 둔다
    var timeSource: TimeSource = TimeSource.Monotonic

    suspend fun aggregate(
        options: QuoteOptions,
        scenario: Long,
        institutions: List<Institution> = INSTITUTIONS,
    ): AggregateResponse {
        val start = timeSource.markNow()
        val elapsed = { start.elapsedNow().inWholeMilliseconds }

        val results = when (options.strategy) {
            Strategy.SEQUENTIAL -> institutions.map { call(it, options, scenario, elapsed) }
            Strategy.PARALLEL -> coroutineScope {
                // 동시에 나가는 호출 수 제한. 외부 기관 rate limit이나 커넥션 풀을 넘지 않게
                val permits = Semaphore(options.concurrency)
                institutions.map { inst ->
                    async { permits.withPermit { call(inst, options, scenario, elapsed) } }
                }.awaitAll()
            }
        }

        val offers = results.filter { it.outcome == Outcome.OK }.sortedBy { it.quote!!.rate }
        return AggregateResponse(
            options = options,
            totalMs = elapsed(),
            succeeded = offers.size,
            failed = results.size - offers.size,
            offers = offers,
            results = results,
        )
    }

    /**
     * 기관 하나 호출. 시도마다 타임아웃을 걸고, 에러 응답만 재시도한다.
     * 타임아웃은 재시도하지 않는다: 이미 느린 기관을 다시 기다리면 전체 응답이 그만큼 늦어진다.
     * 실패는 예외로 올리지 않고 결과로 담아, 한 기관의 실패가 전체 실패로 번지지 않게 한다.
     */
    private suspend fun call(
        inst: Institution,
        options: QuoteOptions,
        scenario: Long,
        elapsed: () -> Long,
    ): InstitutionResult {
        val attempts = mutableListOf<Attempt>()
        for (attempt in 0..options.retries) {
            val s = elapsed()
            try {
                val quote = withTimeout(options.timeoutMs) { client.fetch(inst.code, scenario, attempt) }
                attempts += Attempt(s, elapsed(), Outcome.OK)
                return InstitutionResult(inst.code, inst.name, Outcome.OK, quote, attempts)
            } catch (e: TimeoutCancellationException) {
                attempts += Attempt(s, elapsed(), Outcome.TIMEOUT)
                return InstitutionResult(inst.code, inst.name, Outcome.TIMEOUT, null, attempts)
            } catch (e: CancellationException) {
                throw e // 요청 자체가 취소된 경우는 삼키지 않는다
            } catch (e: Exception) {
                attempts += Attempt(s, elapsed(), Outcome.ERROR)
            }
        }
        return InstitutionResult(inst.code, inst.name, Outcome.ERROR, null, attempts)
    }
}
