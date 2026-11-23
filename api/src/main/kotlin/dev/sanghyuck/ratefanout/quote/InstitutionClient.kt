package dev.sanghyuck.ratefanout.quote

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/** 금융사 한 곳에 대출 조건을 묻는다. 실패하면 예외 */
fun interface InstitutionClient {
    suspend fun fetch(code: String, scenario: Long, attempt: Int): Quote
}

@Component
class HttpInstitutionClient(
    @Value("\${app.mock.base-url}") baseUrl: String,
    env: Environment,
) : InstitutionClient {
    // 서버 포트는 기동이 끝나야 정해지므로 첫 호출 때 만든다
    private val client by lazy {
        val url = baseUrl.ifBlank { "http://localhost:${env.getRequiredProperty("local.server.port")}" }
        WebClient.builder().baseUrl(url).build()
    }

    // 논블로킹 호출: 응답을 기다리는 동안 스레드를 잡지 않는다
    override suspend fun fetch(code: String, scenario: Long, attempt: Int): Quote =
        client.get()
            .uri("/mock/institutions/{code}/quote?scenario={s}&attempt={a}", code, scenario, attempt)
            .retrieve()
            .awaitBody()
}
