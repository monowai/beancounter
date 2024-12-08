package com.beancounter.common.telemetry

import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.Instrumenter
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.opentelemetry.OpenTelemetryLinkErrorEventProcessor
import io.sentry.opentelemetry.SentrySpanProcessor
import io.sentry.protocol.SentryTransaction
import io.sentry.spring.jakarta.SentryTaskDecorator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

/**
 * Sentry Configuration. Assumes the Agent is running.
 *
 * Monitor using OTEL and export to Sentry.
 */
@Suppress("UnstableApiUsage")
@ConditionalOnProperty(
    name = ["sentry.enabled"],
    havingValue = "true",
)
@Configuration
@DependsOn("propertySourcesPlaceholderConfigurer")
class SentryOtelConfig {
    @Bean
    fun sentryTaskDecorator() = SentryTaskDecorator()

    @Bean
    fun otelLinkEventProcessor(): OpenTelemetryLinkErrorEventProcessor = OpenTelemetryLinkErrorEventProcessor()

    @Bean
    fun sdkTraceProvider(): SdkTracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SentrySpanProcessor()).build()

    @Bean
    fun sentryInit(
        @Value("\${sentry.dsn}") dsn: String,
        @Value("\${sentry.environment:local}") sentryEnv: String,
        @Value("\${sentry.debug:false}") debug: String,
        @Value("\${sentry.traces-sample-rate:0.3}") tracesSampleRate: Double,
        @Value("\${sentry.sample-rate:0.3}") sampleRate: Double,
        @Value("\${management.endpoints.web.base-path:/actuator}") managementBasePath: String,
    ): Boolean {
        LoggerFactory
            .getLogger(SentryOtelConfig::class.java)
            .info(
                "SentryConfig: $dsn, environment: $sentryEnv debug: $debug, tracesSampleRate: $tracesSampleRate",
            )

        val sentryFilterConditions =
            listOf(
                Regex(managementBasePath),
                Regex("/favicon\\.ico"),
                Regex("/webjars.*"),
                Regex("/css.*"),
                Regex("/js"),
                Regex("/images"),
                Regex("/api-docs"),
                Regex("/swagger-ui.*"),
                Regex("/swagger-resources"),
            )
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = sentryEnv
            options.enableTracing = true
            options.tracesSampleRate = tracesSampleRate
            options.isDebug = debug.toBoolean()
            options.instrumenter = Instrumenter.OTEL
            options.sampleRate = sampleRate
            options.beforeSendTransaction =
                SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                    filterTransaction(
                        transaction,
                        sentryFilterConditions,
                    )
                }
        }
        return true
    }

    fun filterTransaction(
        transaction: SentryTransaction,
        sentryFilterConditions: List<Regex>,
    ): SentryTransaction? =
        if (sentryFilterConditions.any {
            val otelAttributes = getOtelAttributes(getOtelContext(transaction))
            otelAttributes["http.target"]?.toString()?.let { target ->
                it.containsMatchIn(target)
            } == true
        }
        ) {
            null
        } else {
            transaction
        }

    private fun getOtelContext(transaction: SentryTransaction) = transaction.contexts["otel"] as Map<*, *>

    private fun getOtelAttributes(otelMap: Map<*, *>): Map<*, *> = otelMap["attributes"] as Map<*, *>
}
