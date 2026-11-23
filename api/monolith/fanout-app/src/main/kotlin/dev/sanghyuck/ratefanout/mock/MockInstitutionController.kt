package dev.sanghyuck.ratefanout.mock

import dev.sanghyuck.ratefanout.quote.domain.Quote
import kotlinx.coroutines.delay
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.random.Random

// 가짜 금융사 API. (scenario, code, attempt)가 같으면 지연이랑 결과도 항상 같다
// 그래야 순차/병렬을 같은 조건으로 비교할 수 있다
@RestController
class MockInstitutionController {

  @GetMapping("/mock/institutions/{code}/quote")
  suspend fun getQuote(
    @PathVariable code: String,
    @RequestParam scenario: Long,
    @RequestParam(defaultValue = "0") attempt: Int,
  ): Quote {
    val behavior = behavior(scenario, code, attempt)
    delay(behavior.latencyMs)
    if (behavior.error) {
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "mock institution error")
    }
    return behavior.quote
  }

  data class Behavior(
    val latencyMs: Long,
    val error: Boolean,
    val quote: Quote,
  )

  companion object {
    fun behavior(scenario: Long, code: String, attempt: Int): Behavior {
      val random = Random(scenario * 31 + code.hashCode() * 7 + attempt * 1_000_003L)
      val dice = random.nextDouble()
      val latencyMs = when {
        dice < 0.10 -> 150L + random.nextLong(250) // 금방 에러
        dice < 0.20 -> 2500L + random.nextLong(2500) // 응답 지연
        else -> 250L + random.nextLong(1300)
      }

      // 금리랑 한도는 재시도해도 안 바뀌게 attempt를 빼고 시드를 잡는다
      val quoteRandom = Random(scenario * 31 + code.hashCode())
      val quote = Quote(
        rate = Math.round((4.2 + quoteRandom.nextDouble() * 9) * 100) / 100.0,
        limit = (5 + quoteRandom.nextInt(46)) * 100,
      )
      return Behavior(latencyMs, dice < 0.10, quote)
    }
  }
}
