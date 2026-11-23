package dev.sanghyuck.ratefanout.mock

import dev.sanghyuck.ratefanout.quote.Quote
import kotlinx.coroutines.delay
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.random.Random

/**
 * 가상의 금융사 API. 같은 (scenario, code, attempt)면 항상 같은 지연과 결과를 낸다.
 * 그래야 순차·병렬을 같은 조건에서 비교할 수 있다.
 */
@RestController
class MockInstitutionController {

    @GetMapping("/mock/institutions/{code}/quote")
    suspend fun quote(
        @PathVariable code: String,
        @RequestParam scenario: Long,
        @RequestParam(defaultValue = "0") attempt: Int,
    ): Quote {
        val b = behavior(scenario, code, attempt)
        delay(b.latencyMs) // 논블로킹 대기. 스레드를 재우지 않는다
        if (b.error) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "mock institution error")
        return b.quote
    }

    data class Behavior(val latencyMs: Long, val error: Boolean, val quote: Quote)

    companion object {
        fun behavior(scenario: Long, code: String, attempt: Int): Behavior {
            val r = Random(scenario * 31 + code.hashCode() * 7 + attempt * 1_000_003L)
            val x = r.nextDouble()
            val latency = when {
                x < 0.10 -> 150L + r.nextLong(250) // 빠른 에러
                x < 0.20 -> 2500L + r.nextLong(2500) // 응답 지연
                else -> 250L + r.nextLong(1300) // 정상
            }
            // 금리·한도는 재시도해도 같게
            val q = Random(scenario * 31 + code.hashCode())
            val quote = Quote(rate = Math.round((4.2 + q.nextDouble() * 9) * 100) / 100.0, limit = (5 + q.nextInt(46)) * 100)
            return Behavior(latency, x < 0.10, quote)
        }
    }
}
