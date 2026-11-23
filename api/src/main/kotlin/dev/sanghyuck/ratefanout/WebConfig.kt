package dev.sanghyuck.ratefanout

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}") private val origins: String,
) : WebFluxConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*origins.split(",").map(String::trim).toTypedArray())
            .allowedMethods("GET", "POST")
    }
}
