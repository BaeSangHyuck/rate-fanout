package dev.sanghyuck.ratefanout.coupon.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CouponTest {

  @Test
  fun `남아 있으면 한 장 나가고 다 떨어지면 못 준다`() {
    assertEquals(Coupon(0), Coupon(1).issue())
    assertNull(Coupon(0).issue())
  }
}
