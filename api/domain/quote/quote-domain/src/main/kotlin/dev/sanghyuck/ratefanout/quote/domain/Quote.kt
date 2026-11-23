package dev.sanghyuck.ratefanout.quote.domain

enum class Strategy { SEQUENTIAL, PARALLEL }

enum class Outcome { OK, ERROR, TIMEOUT }

data class Institution(
  val code: String,
  val name: String,
)

val INSTITUTIONS: List<Institution> = ('A'..'L').map { Institution(it.toString(), "금융사 $it") }

// 금융사 응답 본문
data class Quote(
  val rate: Double,
  val limit: Int,
)

// 호출 1번. 시간은 요청 시작 기준 ms
data class Attempt(
  val startMs: Long,
  val endMs: Long,
  val outcome: Outcome,
)

data class InstitutionResult(
  val code: String,
  val name: String,
  val outcome: Outcome,
  val quote: Quote?,
  val attempts: List<Attempt>,
)

// 받은 곳만 금리 낮은 순으로
fun List<InstitutionResult>.bestOffers(): List<InstitutionResult> {
  return filter { it.outcome == Outcome.OK }.sortedBy { it.quote!!.rate }
}
