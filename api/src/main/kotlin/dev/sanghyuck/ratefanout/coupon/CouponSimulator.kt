package dev.sanghyuck.ratefanout.coupon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

enum class IssueMode { NONE, PESSIMISTIC, CONDITIONAL }
enum class IssueStatus { ISSUED, SOLD_OUT, DUPLICATE }

data class CouponOptions(val mode: IssueMode, val requests: Int, val users: Int, val quantity: Int, val gapMs: Long)
data class CouponRunResponse(
    val options: CouponOptions, val totalMs: Long,
    val issued: Int, val overIssued: Int, val remaining: Int,
    val soldOut: Int, val duplicatesBlocked: Int,
    val p50Ms: Long, val p95Ms: Long, val maxMs: Long,
)

/**
 * 선착순 쿠폰 발급. 요청이 한꺼번에 몰릴 때 한정 수량을 넘기지 않고, 같은 사용자에게 두 번 주지 않는지 본다.
 * 사용자 수보다 요청이 많으면 같은 사용자가 연달아 누른 것으로 본다.
 */
@Service
class CouponSimulator(private val jdbc: JdbcClient, private val tx: TransactionTemplate) {

    suspend fun run(o: CouponOptions): CouponRunResponse {
        val couponId = UUID.randomUUID().toString()
        db { jdbc.sql("insert into coupon(id, remaining) values (?, ?)").params(couponId, o.quantity).update() }
        try {
            val started = System.nanoTime()
            val results = coroutineScope {
                (0 until o.requests).map { i ->
                    // 사용자 수가 요청보다 적으면 이웃한 요청이 같은 사용자가 된다(연타)
                    val userId = (i.toLong() * o.users / o.requests).toInt()
                    async {
                        val status = db { issue(couponId, userId, o) }
                        status to (System.nanoTime() - started) / 1_000_000
                    }
                }.awaitAll()
            }
            val totalMs = (System.nanoTime() - started) / 1_000_000
            val issued = db { jdbc.sql("select count(*) from coupon_issue where coupon_id = ?").param(couponId).query(Int::class.java).single() }
            val remaining = db { jdbc.sql("select remaining from coupon where id = ?").param(couponId).query(Int::class.java).single() }
            val latencies = results.map { it.second }.sorted()
            return CouponRunResponse(
                options = o, totalMs = totalMs,
                issued = issued, overIssued = maxOf(0, issued - o.quantity), remaining = remaining,
                soldOut = results.count { it.first == IssueStatus.SOLD_OUT },
                duplicatesBlocked = results.count { it.first == IssueStatus.DUPLICATE },
                p50Ms = percentile(latencies, 50), p95Ms = percentile(latencies, 95), maxMs = latencies.last(),
            )
        } finally {
            db {
                jdbc.sql("delete from coupon_issue where coupon_id = ?").param(couponId).update()
                jdbc.sql("delete from coupon where id = ?").param(couponId).update()
            }
        }
    }

    private fun issue(couponId: String, userId: Int, o: CouponOptions): IssueStatus = try {
        tx.execute {
            when (o.mode) {
                IssueMode.NONE, IssueMode.PESSIMISTIC -> {
                    val lock = if (o.mode == IssueMode.PESSIMISTIC) " for update" else ""
                    val remaining = jdbc.sql("select remaining from coupon where id = ?$lock").param(couponId).query(Int::class.java).single()
                    Thread.sleep(o.gapMs) // 발급 자격 확인 등 처리 시간. 락 모드에서는 이 시간 동안 락을 잡고 있다
                    if (remaining <= 0) return@execute IssueStatus.SOLD_OUT
                    jdbc.sql("update coupon set remaining = ? where id = ?").params(remaining - 1, couponId).update()
                }
                IssueMode.CONDITIONAL -> {
                    Thread.sleep(o.gapMs) // 같은 처리 시간을 락 밖에서 쓴다
                    // 확인과 차감을 한 문장으로: 남은 수량이 있을 때만 줄인다
                    val updated = jdbc.sql("update coupon set remaining = remaining - 1 where id = ? and remaining > 0").param(couponId).update()
                    if (updated == 0) return@execute IssueStatus.SOLD_OUT
                }
            }
            // (쿠폰, 사용자) 유니크 키가 중복 발급을 막는다. 위반하면 트랜잭션이 롤백돼 차감도 되돌아간다
            jdbc.sql("insert into coupon_issue(coupon_id, user_id) values (?, ?)").params(couponId, userId).update()
            IssueStatus.ISSUED
        }!!
    } catch (e: DuplicateKeyException) {
        IssueStatus.DUPLICATE
    }

    private fun percentile(sorted: List<Long>, p: Int): Long = sorted[((sorted.size - 1) * p / 100.0).toInt()]

    private suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
