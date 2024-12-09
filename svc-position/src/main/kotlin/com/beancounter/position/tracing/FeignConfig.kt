package com.beancounter.position.tracing

import feign.Feign
import io.sentry.openfeign.SentryCapability
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeignConfig {
    @Bean
    fun feignBuilder(): Feign.Builder =
        Feign
            .builder()
            .addCapability(SentryCapability())
}