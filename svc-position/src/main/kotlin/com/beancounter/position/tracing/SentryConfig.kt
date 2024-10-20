package com.beancounter.position.tracing

import io.sentry.spring.jakarta.SentryTaskDecorator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// @ConditionalOnProperty(name = ["\${sentry.enabled}"], havingValue = "true")
@Configuration
class SentryConfig(
    @Value("\${sentry.dsn}") val dsn: String,
) {
    init {
        LoggerFactory
            .getLogger(SentryConfig::class.java)
            .info("SentryConfig: $dsn")
    }

    @Bean
    fun sentryTaskDecorator() = SentryTaskDecorator()

//    @Bean
//    fun otelLinkEventProcessor(): OpenTelemetryLinkErrorEventProcessor = OpenTelemetryLinkErrorEventProcessor()
//
//    @Bean
//    fun getOpenTelemetry(): OpenTelemetrySdk =
//        OpenTelemetrySdk
//            .builder()
//            .setTracerProvider(
//                SdkTracerProvider
//                    .builder()
//                    .addSpanProcessor(SentrySpanProcessor())
//                    .build(),
//            ).setPropagators(ContextPropagators.create(SentryPropagator()))
//            .buildAndRegisterGlobal()
}
