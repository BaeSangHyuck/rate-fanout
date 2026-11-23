package dev.sanghyuck.ratefanout.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.redis.url")
class RedisConfig {
  @Bean(destroyMethod = "shutdown")
  fun redissonClient(@Value("\${app.redis.url}") url: String): RedissonClient {
    val config = Config()
    config.useSingleServer().address = url
    return Redisson.create(config)
  }
}
