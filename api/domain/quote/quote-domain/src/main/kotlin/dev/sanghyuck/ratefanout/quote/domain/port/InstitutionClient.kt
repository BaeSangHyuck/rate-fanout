package dev.sanghyuck.ratefanout.quote.domain.port

import dev.sanghyuck.ratefanout.quote.domain.Quote

// 금융사 호출 포트. HTTP 구현은 application의 adapter에 있고 테스트에선 가짜로 바꿔 끼운다
fun interface InstitutionClient {
  suspend fun fetch(code: String, scenario: Long, attempt: Int): Quote
}
