package dev.sanghyuck.ratefanout.coupon.application.adapter.redis

import dev.sanghyuck.ratefanout.coupon.domain.IssueStatus
import dev.sanghyuck.ratefanout.coupon.domain.port.CouponIssueGate
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConditionalOnProperty("app.redis.url") // 레디스 주소가 있을 때만 REDIS 모드를 켠다
class RedisCouponIssueGate(
  private val redissonClient: RedissonClient,
) : CouponIssueGate {
  override fun open(couponId: String, quantity: Int) {
    val stock = redissonClient.getAtomicLong(stockKey(couponId))
    stock.set(quantity.toLong())
    stock.expire(KEY_TTL) // 정리가 안 되고 죽어도 키가 남지 않게
  }

  // 중복 확인, 수량 확인, 차감, 사용자 기록을 스크립트 하나로 묶는다. 레디스는 스크립트를 한 번에 하나씩 돌려서 사이에 끼어들 틈이 없다
  override fun tryAcquire(couponId: String, userId: Int): IssueStatus {
    val result = redissonClient.getScript(StringCodec.INSTANCE).eval<Long>(
      RScript.Mode.READ_WRITE,
      ACQUIRE_SCRIPT,
      RScript.ReturnType.LONG,
      listOf(stockKey(couponId), usersKey(couponId)),
      userId.toString(),
    )
    return when (result) {
      0L -> IssueStatus.ISSUED
      1L -> IssueStatus.SOLD_OUT
      else -> IssueStatus.DUPLICATE
    }
  }

  override fun close(couponId: String) {
    redissonClient.keys.delete(stockKey(couponId), usersKey(couponId))
  }

  private fun stockKey(couponId: String) = "coupon:$couponId:stock"

  private fun usersKey(couponId: String) = "coupon:$couponId:users"

  companion object {
    private val KEY_TTL = Duration.ofMinutes(10)

    private val ACQUIRE_SCRIPT = """
      if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return 2 end
      if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then return 1 end
      redis.call('decr', KEYS[1])
      redis.call('sadd', KEYS[2], ARGV[1])
      redis.call('pexpire', KEYS[2], redis.call('pttl', KEYS[1]))
      return 0
    """.trimIndent()
  }
}
