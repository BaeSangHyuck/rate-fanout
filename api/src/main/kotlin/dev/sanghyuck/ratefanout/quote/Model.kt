package dev.sanghyuck.ratefanout.quote

enum class Strategy { SEQUENTIAL, PARALLEL }

enum class Outcome { OK, ERROR, TIMEOUT }

/** 금융사 응답 본문 */
data class Quote(val rate: Double, val limit: Int)

/** 호출 1회. 요청 시작 기준 상대 시간(ms) */
data class Attempt(val startMs: Long, val endMs: Long, val outcome: Outcome)

data class InstitutionResult(
    val code: String,
    val name: String,
    val outcome: Outcome,
    val quote: Quote?,
    val attempts: List<Attempt>,
)

data class QuoteOptions(
    val strategy: Strategy,
    val timeoutMs: Long,
    val retries: Int,
    val concurrency: Int,
)

data class AggregateResponse(
    val options: QuoteOptions,
    val totalMs: Long,
    val succeeded: Int,
    val failed: Int,
    /** 성공한 기관만 금리 오름차순 */
    val offers: List<InstitutionResult>,
    val results: List<InstitutionResult>,
)

data class Institution(val code: String, val name: String)

val INSTITUTIONS: List<Institution> = ('A'..'L').map { Institution(it.toString(), "금융사 $it") }
