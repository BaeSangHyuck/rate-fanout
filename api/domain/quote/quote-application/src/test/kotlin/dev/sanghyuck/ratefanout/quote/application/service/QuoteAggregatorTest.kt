package dev.sanghyuck.ratefanout.quote.application.service

import dev.sanghyuck.ratefanout.quote.application.dto.QuoteRequestDto
import dev.sanghyuck.ratefanout.quote.domain.Institution
import dev.sanghyuck.ratefanout.quote.domain.Outcome
import dev.sanghyuck.ratefanout.quote.domain.Quote
import dev.sanghyuck.ratefanout.quote.domain.Strategy
import dev.sanghyuck.ratefanout.quote.domain.port.InstitutionClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 가상 시간(runTest)으로 돌려서 지연·타임아웃을 실제로 기다리지 않고 정확한 ms로 검증한다 */
class QuoteAggregatorTest {

  private val insts = listOf("A", "B", "C").map { Institution(it, "금융사 $it") }

  /** 기관별 (시도 번호 → 지연ms, 에러 여부) */
  private fun fakeClient(plan: Map<String, List<Pair<Long, Boolean>>>) = InstitutionClient { code, _, attempt ->
    val (latency, error) = plan.getValue(code)[attempt]
    delay(latency)
    if (error) error("503")
    Quote(rate = latency / 100.0, limit = 1000)
  }

  private fun opts(strategy: Strategy, timeoutMs: Long = 5_000, retries: Int = 0, concurrency: Int = 12) =
    QuoteRequestDto(strategy, timeoutMs, retries, concurrency)

  private val plan = mapOf(
    "A" to listOf(300L to false),
    "B" to listOf(800L to false),
    "C" to listOf(500L to false),
  )

  @Test
  fun `순차 호출은 지연의 합만큼 걸린다`() = runTest {
    val agg = QuoteAggregator(fakeClient(plan)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.SEQUENTIAL), 1, insts)
    assertEquals(1600, res.totalMs)
  }

  @Test
  fun `병렬 호출은 가장 느린 호출만큼 걸린다`() = runTest {
    val agg = QuoteAggregator(fakeClient(plan)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.PARALLEL), 1, insts)
    assertEquals(800, res.totalMs)
    assertEquals(listOf("A", "C", "B"), res.offers.map { it.code }) // 금리 오름차순
  }

  @Test
  fun `동시 호출 수를 2로 제한하면 세 번째 호출은 앞의 하나가 끝난 뒤 시작한다`() = runTest {
    val agg = QuoteAggregator(fakeClient(plan)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.PARALLEL, concurrency = 2), 1, insts)
    // A(0~300) B(0~800) → C는 A가 끝난 300에 시작해 800에 끝남
    assertEquals(300, res.results.first { it.code == "C" }.attempts.single().startMs)
    assertEquals(800, res.totalMs)
  }

  @Test
  fun `타임아웃된 기관은 빼고 나머지로 응답한다`() = runTest {
    val slow = plan + ("B" to listOf(4_000L to false))
    val agg = QuoteAggregator(fakeClient(slow)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.PARALLEL, timeoutMs = 1_000), 1, insts)
    assertEquals(1_000, res.totalMs) // 4초를 기다리지 않는다
    assertEquals(Outcome.TIMEOUT, res.results.first { it.code == "B" }.outcome)
    assertEquals(2, res.succeeded)
  }

  @Test
  fun `에러는 재시도하고, 재시도에서 성공하면 성공으로 본다`() = runTest {
    val flaky = plan + ("A" to listOf(200L to true, 300L to false))
    val agg = QuoteAggregator(fakeClient(flaky)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.PARALLEL, retries = 1), 1, insts)
    val a = res.results.first { it.code == "A" }
    assertEquals(Outcome.OK, a.outcome)
    assertEquals(listOf(Outcome.ERROR, Outcome.OK), a.attempts.map { it.outcome })
    assertEquals(listOf(0L to 200L, 200L to 500L), a.attempts.map { it.startMs to it.endMs })
  }

  @Test
  fun `재시도 횟수를 다 쓰면 에러로 남기고 전체 요청은 실패하지 않는다`() = runTest {
    val broken = plan + ("A" to listOf(100L to true, 100L to true))
    val agg = QuoteAggregator(fakeClient(broken)).apply { timeSource = testScheduler.timeSource }
    val res = agg.aggregate(opts(Strategy.PARALLEL, retries = 1), 1, insts)
    assertEquals(Outcome.ERROR, res.results.first { it.code == "A" }.outcome)
    assertEquals(2, res.succeeded)
    assertEquals(1, res.failed)
  }
}
