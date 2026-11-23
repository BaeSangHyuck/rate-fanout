package dev.sanghyuck.ratefanout.order

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.random.Random

enum class LockMode { NONE, PESSIMISTIC }
enum class OrderStatus { PAID, COMPENSATED, SOLD_OUT }

data class OrderOptions(
    val mode: LockMode, val orders: Int, val stock: Int,
    val paymentFailPct: Int, val publishFailPct: Int, val gapMs: Long, val seed: Long,
)
data class OrderTrace(val no: Int, val status: OrderStatus, val startMs: Long, val endMs: Long)
data class OutboxStats(
    val events: Int, val consumed: Int, val duplicatesIgnored: Int,
    val publishFailures: Int, val lostAcks: Int, val rounds: Int, val pending: Int,
)
data class OrderRunResponse(
    val options: OrderOptions, val totalMs: Long,
    val finalStock: Int, val expectedStock: Int, val consistent: Boolean,
    val paid: Int, val compensated: Int, val soldOut: Int,
    val orders: List<OrderTrace>, val outbox: OutboxStats,
)

/**
 * 매장 여러 곳에서 같은 상품 주문이 동시에 들어오는 상황을 실제 DB 트랜잭션으로 돌린다.
 * 재고 차감 → 커밋 → 외부 결제 승인, 승인 실패 시 보상 트랜잭션으로 재고 복구.
 * 재고 변경과 같은 트랜잭션에 아웃박스 이벤트를 쓰고, 릴레이가 at-least-once로 발행한다.
 */
@Service
class OrderSimulator(private val jdbc: JdbcClient, private val tx: TransactionTemplate) {

    suspend fun run(o: OrderOptions): OrderRunResponse {
        val runId = UUID.randomUUID().toString()
        db { jdbc.sql("insert into product(id, stock) values (?, ?)").params(runId, o.stock).update() }
        try {
            val started = System.nanoTime()
            val now = { (System.nanoTime() - started) / 1_000_000 }
            val trace = coroutineScope { (1..o.orders).map { no -> async { order(runId, no, o, now) } }.awaitAll() }
            val totalMs = now()
            val finalStock = db { jdbc.sql("select stock from product where id = ?").param(runId).query(Int::class.java).single() }
            val paid = trace.count { it.status == OrderStatus.PAID }
            return OrderRunResponse(
                options = o, totalMs = totalMs,
                finalStock = finalStock, expectedStock = o.stock - paid, consistent = finalStock == o.stock - paid,
                paid = paid,
                compensated = trace.count { it.status == OrderStatus.COMPENSATED },
                soldOut = trace.count { it.status == OrderStatus.SOLD_OUT },
                orders = trace, outbox = relay(runId, o),
            )
        } finally {
            db {
                jdbc.sql("delete from processed_event where run_id = ?").param(runId).update()
                jdbc.sql("delete from outbox where run_id = ?").param(runId).update()
                jdbc.sql("delete from product where id = ?").param(runId).update()
            }
        }
    }

    private suspend fun order(runId: String, no: Int, o: OrderOptions, now: () -> Long): OrderTrace {
        val start = now()
        if (!db { changeStock(runId, -1, no, o) }) return OrderTrace(no, OrderStatus.SOLD_OUT, start, now())
        // 외부 결제 승인: DB 트랜잭션 밖에서 기다린다(커넥션을 잡지 않는다)
        val rnd = Random(o.seed * 1_000 + no)
        delay(rnd.nextLong(150, 400))
        if (rnd.nextInt(100) < o.paymentFailPct) {
            db { changeStock(runId, +1, no, o) } // 보상 트랜잭션: 이미 커밋된 차감을 반대 연산으로 되돌린다
            return OrderTrace(no, OrderStatus.COMPENSATED, start, now())
        }
        return OrderTrace(no, OrderStatus.PAID, start, now())
    }

    /** 조회 후 저장(read-modify-write). 락이 없으면 사이에 끼어든 변경이 덮어써진다 */
    private fun changeStock(runId: String, delta: Int, no: Int, o: OrderOptions): Boolean = tx.execute {
        val lock = if (o.mode == LockMode.PESSIMISTIC) " for update" else ""
        val stock = jdbc.sql("select stock from product where id = ?$lock").param(runId).query(Int::class.java).single()
        Thread.sleep(o.gapMs) // 조회와 저장 사이의 처리 시간
        if (delta < 0 && stock <= 0) return@execute false
        jdbc.sql("update product set stock = ? where id = ?").params(stock + delta, runId).update()
        jdbc.sql("insert into outbox(event_id, run_id, type, order_no) values (?, ?, ?, ?)")
            .params(UUID.randomUUID().toString(), runId, if (delta < 0) "STOCK_DECREASED" else "STOCK_RESTORED", no)
            .update()
        true
    }!!

    /**
     * 아웃박스 릴레이. 실서비스에서는 스케줄러가 주기적으로 돌고, 데모에서는 주문이 끝난 뒤 같은 요청 안에서 돌린다.
     * 전달 실패는 남겨서 다음 주기에 재시도하고, 전달은 됐지만 응답을 못 받으면 다시 보낸다(at-least-once).
     */
    private suspend fun relay(runId: String, o: OrderOptions): OutboxStats {
        val rnd = Random(o.seed)
        val events = db { jdbc.sql("select count(*) from outbox where run_id = ?").param(runId).query(Int::class.java).single() }
        var consumed = 0; var duplicates = 0; var failures = 0; var lostAcks = 0; var rounds = 0
        var pending = pendingEvents(runId)
        while (pending.isNotEmpty() && rounds < MAX_RELAY_ROUNDS) {
            rounds++
            for (eventId in pending) {
                val r = rnd.nextInt(100)
                if (r < o.publishFailPct / 2) { failures++; continue }
                if (db { consume(runId, eventId) }) consumed++ else duplicates++
                if (r < o.publishFailPct) { lostAcks++; continue }
                db { jdbc.sql("update outbox set published = true where event_id = ?").param(eventId).update() }
            }
            pending = pendingEvents(runId)
        }
        return OutboxStats(events, consumed, duplicates, failures, lostAcks, rounds, pending.size)
    }

    private suspend fun pendingEvents(runId: String): List<String> = db {
        jdbc.sql("select event_id from outbox where run_id = ? and published = false").param(runId).query(String::class.java).list().filterNotNull()
    }

    /** 컨슈머: 이미 처리한 eventId면 무시한다(멱등 처리) */
    private fun consume(runId: String, eventId: String): Boolean = try {
        jdbc.sql("insert into processed_event(event_id, run_id) values (?, ?)").params(eventId, runId).update()
        true
    } catch (e: DuplicateKeyException) {
        false
    }

    private suspend fun <T> db(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        const val MAX_RELAY_ROUNDS = 50
    }
}
