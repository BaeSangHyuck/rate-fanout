package dev.sanghyuck.ratefanout

import dev.sanghyuck.ratefanout.coupon.application.dto.CouponRunResponseDto
import dev.sanghyuck.ratefanout.order.application.dto.OrderRunResponseDto
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 배포 구성(Postgres + Redis + Kafka)을 컨테이너로 띄워서 H2에서 본 결과가 그대로 나오는지 확인한다. 도커가 없으면 건너뛴다 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["app.kafka.enabled=true"])
class InfraIntegrationTests {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres = PostgreSQLContainer("postgres:17-alpine")

    @Container
    @ServiceConnection
    @JvmStatic
    val kafka = KafkaContainer("apache/kafka:3.9.1")

    @Container
    @JvmStatic
    val redis = GenericContainer<Nothing>("redis:7.4-alpine").apply { withExposedPorts(6379) }

    @DynamicPropertySource
    @JvmStatic
    fun redisUrl(registry: DynamicPropertyRegistry) {
      registry.add("app.redis.url") { "redis://${redis.host}:${redis.getMappedPort(6379)}" }
    }
  }

  @LocalServerPort
  var port: Int = 0

  private inline fun <reified T : Any> post(path: String): T =
    WebTestClient.bindToServer().baseUrl("http://localhost:$port").responseTimeout(Duration.ofSeconds(90)).build()
      .post().uri(path).exchange().expectStatus().isOk.expectBody<T>().returnResult().responseBody!!

  @Test
  fun `Postgres에서도 비관적 락이면 재고가 맞고, 카프카로 중복 전달된 이벤트는 한 번씩만 처리된다`() {
    val r = post<OrderRunResponseDto>("/api/orders/run?mode=PESSIMISTIC&orders=20&stock=10&paymentFailPct=30&publishFailPct=50&seed=5")
    assertTrue(r.consistent, "DB ${r.finalStock}, 기대 ${r.expectedStock}")
    assertEquals(0, r.outbox.pending)
    assertEquals(r.outbox.events, r.outbox.consumed)
    assertTrue(r.outbox.duplicatesIgnored > 0)
  }

  @Test
  fun `Postgres에서도 락 없이 조회 후 저장하면 재고가 어긋난다`() {
    val r = post<OrderRunResponseDto>("/api/orders/run?mode=NONE&orders=20&stock=10&paymentFailPct=0&gapMs=50")
    assertTrue(!r.consistent, "lost update가 나야 한다: DB ${r.finalStock}, 기대 ${r.expectedStock}")
  }

  @Test
  fun `레디스 게이트를 거치면 수량만큼만 DB까지 가고 같은 사용자는 한 번만 받는다`() {
    val r = post<CouponRunResponseDto>("/api/coupons/run?mode=REDIS&requests=300&users=250&quantity=100")
    assertEquals(100, r.issued)
    assertEquals(0, r.remaining)
    assertEquals(300, r.issued + r.soldOut + r.duplicatesBlocked)
    assertTrue(r.duplicatesBlocked > 0)
  }

  @Test
  fun `Postgres에서도 조건부 차감은 수량을 넘기지 않는다`() {
    val r = post<CouponRunResponseDto>("/api/coupons/run?mode=CONDITIONAL&requests=300&users=250&quantity=100")
    assertEquals(100, r.issued)
    assertEquals(0, r.remaining)
  }
}
