package dev.sanghyuck.ratefanout.order.domain.port

// 아웃박스에 쌓인 이벤트를 컨슈머 쪽으로 내보낸다. 실패하면 예외를 던지고, 릴레이가 다음 라운드에 다시 보낸다
fun interface OrderEventPublisher {
  fun publish(eventId: String, runId: String)
}
