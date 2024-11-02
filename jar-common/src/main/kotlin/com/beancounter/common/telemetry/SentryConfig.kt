package com.beancounter.common.telemetry

import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.Instrumenter
import io.sentry.Sentry
import io.sentry.opentelemetry.OpenTelemetryLinkErrorEventProcessor
import io.sentry.opentelemetry.SentrySpanProcessor
import io.sentry.spring.jakarta.SentryTaskDecorator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(name = ["sentry.dsn"], matchIfMissing = false)
@Configuration
class SentryConfig(
    @Value("\${sentry.dsn}") val dsn: String,
    @Value("\${sentry.environment:local}") val sentryEnv: String,
    @Value("\${sentry.debug:false}") val debug: String,
) {
    init {
        LoggerFactory
            .getLogger(SentryConfig::class.java)
            .info("SentryConfig: $dsn, environment: $sentryEnv")
    }

    @Bean
    fun sentryTaskDecorator() = SentryTaskDecorator()

    @Bean
    fun otelLinkEventProcessor(): OpenTelemetryLinkErrorEventProcessor = OpenTelemetryLinkErrorEventProcessor()

    @Bean
    fun sdkTraceProvider(): SdkTracerProvider =
        SdkTracerProvider
            .builder()
            .addSpanProcessor(SentrySpanProcessor())
            .build()

//    @Bean
//    fun getOpenTelemetry(sentryInit: Boolean): OpenTelemetrySdk =
//        OpenTelemetrySdk
//            .builder()
//            .setTracerProvider(
//                SdkTracerProvider
//                    .builder()
//                    .addSpanProcessor(SentrySpanProcessor())
//                    .build(),
//            ).setPropagators(ContextPropagators.create(SentryPropagator()))
//            .buildAndRegisterGlobal()

    @Bean
    fun sentryInit(): Boolean {
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = sentryEnv
            options.enableTracing = true
            options.tracesSampleRate = 1.0
            options.isDebug = debug.toBoolean()
            options.instrumenter = Instrumenter.OTEL
        }
        return true
    }
}
