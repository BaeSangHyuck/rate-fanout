package dev.sanghyuck.ratefanout.quote.application.adapter

import dev.sanghyuck.ratefanout.quote.domain.Quote
import dev.sanghyuck.ratefanout.quote.domain.port.InstitutionClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider

@Component
class HttpInstitutionClient(
  @Value("\${app.mock.base-url}") baseUrl: String,
  // 동시에 필요한 커넥션 = 초당 조회 수 × 기관 12곳 × 호출당 시간(약 1초). 60rps면 700~900개다.
  // Reactor Netty 기본 풀이나 500이면 풀 앞에서 기다리다 타임아웃·즉시 에러로 떨어졌다(docs/load-test.md). 0이면 기본 풀
  @Value("\${app.quote.max-connections:2000}") maxConnections: Int,
  env: Environment,
) : InstitutionClient {
  // 포트는 서버가 다 뜬 다음에 정해져서 첫 호출 때 만든다
  private val webClient by lazy {
    val url = baseUrl.ifBlank { "http://localhost:${env.getRequiredProperty("local.server.port")}" }
    val builder = WebClient.builder().baseUrl(url)
    if (maxConnections > 0) {
      // metrics(true): 풀의 사용 중·대기 커넥션 수를 프로메테우스로 내보낸다. 이번 병목도 이 지표가 있었으면 바로 보였다
      val pool = ConnectionProvider.builder("institution").maxConnections(maxConnections).metrics(true).build()
      builder.clientConnector(ReactorClientHttpConnector(HttpClient.create(pool)))
    }
    builder.build()
  }

  override suspend fun fetch(code: String, scenario: Long, attempt: Int): Quote {
    return webClient.get()
      .uri("/mock/institutions/{code}/quote?scenario={scenario}&attempt={attempt}", code, scenario, attempt)
      .retrieve()
      .awaitBody()
  }
}
