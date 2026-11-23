package dev.sanghyuck.ratefanout

import dev.sanghyuck.ratefanout.quote.application.dto.QuoteResponseDto
import org.junit.jupiter.api.Test
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 HTTP로 모의 금융사 12곳을 호출해 순차와 병렬을 비교한다 */
// 테스트는 기본으로 지표 내보내기를 꺼 두므로 프로메테우스 엔드포인트 확인을 위해 켠다
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTests {

  @LocalServerPort
  var port: Int = 0

  private fun fetch(query: String): QuoteResponseDto {
    val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port")
      .responseTimeout(java.time.Duration.ofSeconds(60)).build()
    return client.get().uri("/api/quotes?$query").exchange()
      .expectStatus().isOk
      .expectBody<QuoteResponseDto>().returnResult().responseBody!!
  }

  @Test
  fun `같은 시나리오에서 병렬 호출이 순차 호출보다 빠르고 결과는 같다`() {
    val seq = fetch("strategy=SEQUENTIAL&scenario=7&timeoutMs=2000&retries=1")
    val par = fetch("strategy=PARALLEL&scenario=7&timeoutMs=2000&retries=1")

    assertEquals(12, par.results.size)
    assertEquals(seq.results.map { it.outcome }, par.results.map { it.outcome })
    assertTrue(par.totalMs * 3 < seq.totalMs, "parallel=${par.totalMs}ms sequential=${seq.totalMs}ms")
    assertTrue(par.totalMs < 2000 + 400 + 1000, "타임아웃·재시도를 넘겨 기다리지 않는다: ${par.totalMs}ms")
  }

  @Test
  fun `잘못된 파라미터는 400`() {
    WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
      .get().uri("/api/quotes?timeoutMs=5").exchange().expectStatus().isBadRequest
  }

  @Test
  fun `프로메테우스 엔드포인트에 HTTP 요청 지표가 나온다`() {
    val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    client.get().uri("/api/quotes?timeoutMs=5").exchange()
    val body = client.get().uri("/actuator/prometheus").exchange()
      .expectStatus().isOk
      .expectBody<String>().returnResult().responseBody!!
    assertTrue(body.contains("http_server_requests_seconds"), "http_server_requests 지표가 없다")
  }
}
