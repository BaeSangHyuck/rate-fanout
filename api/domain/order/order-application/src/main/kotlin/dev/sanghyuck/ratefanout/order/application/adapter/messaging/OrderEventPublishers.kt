package dev.sanghyuck.ratefanout.order.application.adapter.messaging

import dev.sanghyuck.ratefanout.order.application.service.OrderEventConsumer
import dev.sanghyuck.ratefanout.order.domain.port.OrderEventPublisher
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

const val ORDER_STOCK_EVENT_TOPIC = "order-stock-event"

// 브로커 없이 띄울 때(무료 배포, 로컬 기본값). 컨슈머를 같은 프로세스에서 바로 부른다
@Component
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "false", matchIfMissing = true)
class DirectOrderEventPublisher(
  private val orderEventConsumer: OrderEventConsumer,
) : OrderEventPublisher {
  override fun publish(eventId: String, runId: String) {
    orderEventConsumer.consume(eventId, runId)
  }
}

// runId를 키로 보내서 한 실행의 이벤트는 같은 파티션에 순서대로 쌓인다
@Component
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "true")
class KafkaOrderEventPublisher(
  private val kafkaTemplate: KafkaTemplate<String, String>,
) : OrderEventPublisher {
  override fun publish(eventId: String, runId: String) {
    // 브로커 ack까지 기다린다. ack를 못 받으면 예외가 나고 published가 안 찍혀 다음 라운드에 다시 보내진다
    kafkaTemplate.send(ORDER_STOCK_EVENT_TOPIC, runId, eventId).get(5, TimeUnit.SECONDS)
  }
}

@Component
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "true")
class OrderEventKafkaListener(
  private val orderEventConsumer: OrderEventConsumer,
) {
  @KafkaListener(topics = [ORDER_STOCK_EVENT_TOPIC], groupId = "order-stock-consumer")
  fun listen(record: ConsumerRecord<String, String>) {
    orderEventConsumer.consume(eventId = record.value(), runId = record.key())
  }
}

@Configuration
@ConditionalOnProperty(name = ["app.kafka.enabled"], havingValue = "true")
class OrderKafkaConfig {
  @Bean
  fun orderStockEventTopic(): NewTopic = TopicBuilder.name(ORDER_STOCK_EVENT_TOPIC).partitions(3).replicas(1).build()
}
