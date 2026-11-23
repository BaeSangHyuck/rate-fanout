package dev.sanghyuck.ratefanout.quote.application.adapter

import dev.sanghyuck.ratefanout.quote.domain.Quote
import dev.sanghyuck.ratefanout.quote.domain.port.InstitutionClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class HttpInstitutionClient(
  @Value("\${app.mock.base-url}") baseUrl: String,
  env: Environment,
) : InstitutionClient {
  // 포트는 서버가 다 뜬 다음에 정해져서 첫 호출 때 만든다
  private val webClient by lazy {
    val url = baseUrl.ifBlank { "http://localhost:${env.getRequiredProperty("local.server.port")}" }
    WebClient.builder().baseUrl(url).build()
  }

  override suspend fun fetch(code: String, scenario: Long, attempt: Int): Quote {
    return webClient.get()
      .uri("/mock/institutions/{code}/quote?scenario={scenario}&attempt={attempt}", code, scenario, attempt)
      .retrieve()
      .awaitBody()
  }
}
