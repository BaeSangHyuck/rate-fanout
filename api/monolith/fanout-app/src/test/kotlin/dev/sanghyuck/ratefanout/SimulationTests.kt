package dev.sanghyuck.ratefanout

import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunResponseDto
import dev.sanghyuck.ratefanout.order.application.dto.OrderRunResponseDto
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 실제 DB 트랜잭션으로 동시 주문·선착순 발급을 돌려, 방식별로 정합성이 지켜지는지 확인한다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulationTests {

  @LocalServerPort
  var port: Int = 0

  private inline fun <reified T : Any> post(path: String): T =
    WebTestClient.bindToServer().baseUrl("http://localhost:$port").responseTimeout(Duration.ofSeconds(60)).build()
      .post().uri(path).exchange().expectStatus().isOk.expectBody<T>().returnResult().responseBody!!

  @Test
  fun `비관적 락이면 재고가 결제 완료 건수와 맞고 초과 판매가 없다`() {
    val r = post<OrderRunResponseDto>("/api/orders/run?mode=PESSIMISTIC&orders=20&stock=10&paymentFailPct=30&seed=3")
    assertTrue(r.consistent, "DB ${r.finalStock}, 기대 ${r.expectedStock}")
    assertTrue(r.paid <= 10)
    assertEquals(20, r.paid + r.compensated + r.soldOut)
  }

  @Test
  fun `락 없이 조회 후 저장하면 차감이 사라져 재고가 어긋난다`() {
    val r = post<OrderRunResponseDto>("/api/orders/run?mode=NONE&orders=20&stock=10&paymentFailPct=0&gapMs=50")
    assertFalse(r.consistent, "lost update가 나야 한다: DB ${r.finalStock}, 기대 ${r.expectedStock}")
  }

  @Test
  fun `아웃박스 이벤트는 중복 전달돼도 컨슈머가 한 번씩만 처리한다`() {
    val r = post<OrderRunResponseDto>("/api/orders/run?mode=PESSIMISTIC&orders=20&stock=10&publishFailPct=50&seed=5")
    assertEquals(0, r.outbox.pending)
    assertEquals(r.outbox.events, r.outbox.consumed)
    assertTrue(r.outbox.duplicatesIgnored > 0)
  }

  @Test
  fun `조건부 차감과 비관적 락은 수량을 넘기지 않고 같은 사용자에게 한 번만 준다`() {
    for (mode in listOf("CONDITIONAL", "PESSIMISTIC")) {
      val r = post<CouponRunResponseDto>("/api/coupons/run?mode=$mode&requests=300&users=250&quantity=100")
      assertEquals(100, r.issued, mode)
      assertEquals(0, r.remaining, mode)
      assertEquals(300, r.issued + r.soldOut + r.duplicatesBlocked, mode)
    }
  }

  @Test
  fun `락 없이 조회 후 저장하면 수량보다 많이 발급된다`() {
    val r = post<CouponRunResponseDto>("/api/coupons/run?mode=NONE&requests=300&users=300&quantity=100&gapMs=10")
    assertTrue(r.overIssued > 0, "issued=${r.issued}")
  }

  @Test
  fun `레디스가 없는 서버에서 REDIS 모드는 503`() {
    WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
      .post().uri("/api/coupons/run?mode=REDIS").exchange().expectStatus().isEqualTo(503)
  }
}
