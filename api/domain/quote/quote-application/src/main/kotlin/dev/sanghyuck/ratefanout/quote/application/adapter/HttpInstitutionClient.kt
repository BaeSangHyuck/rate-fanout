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
  // 요청 하나가 기관 12곳을 동시에 부르니, 동시 요청 수 × 12만큼 커넥션이 필요하다.
  // Reactor Netty 기본값(코어 수 × 2, 최소 16)이면 부하 때 풀 앞에서 기다리다 타임아웃으로 떨어진다. 0이면 기본값
  @Value("\${app.quote.max-connections:500}") maxConnections: Int,
  env: Environment,
) : InstitutionClient {
  // 포트는 서버가 다 뜬 다음에 정해져서 첫 호출 때 만든다
  private val webClient by lazy {
    val url = baseUrl.ifBlank { "http://localhost:${env.getRequiredProperty("local.server.port")}" }
    val builder = WebClient.builder().baseUrl(url)
    if (maxConnections > 0) {
      val pool = ConnectionProvider.builder("institution").maxConnections(maxConnections).build()
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
