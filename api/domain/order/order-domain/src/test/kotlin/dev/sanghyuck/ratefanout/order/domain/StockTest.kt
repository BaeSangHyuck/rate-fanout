package dev.sanghyuck.ratefanout.order.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockTest {

  @Test
  fun `재고가 있으면 하나 빠지고 없으면 못 뺀다`() {
    assertEquals(Stock(0), Stock(1).decrease())
    assertNull(Stock(0).decrease())
  }

  @Test
  fun `보상하면 하나 돌아온다`() {
    assertEquals(Stock(1), Stock(0).restore())
  }
}
