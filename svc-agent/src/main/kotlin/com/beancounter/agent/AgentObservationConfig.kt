package com.beancounter.agent

import io.micrometer.common.KeyValue
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Adds LLM token usage to the `gen_ai.client.operation` span.
 *
 * Spring AI records token counts only on the `gen_ai.client.token.usage`
 * **meter** — its observation convention does not put usage on the span, so the
 * `chat <model>` span carried model/system/finish-reason but no tokens, and
 * Sentry's AI monitoring (and any cost baseline) had nothing to read. This
 * [ObservationFilter] runs at observation stop (response present) and copies the
 * usage onto the context as `gen_ai.*` high-cardinality key-values, which the
 * tracing observation handler then writes as span attributes.
 *
 * Names follow the OpenTelemetry GenAI semantic conventions
 * (`gen_ai.usage.input_tokens` / `output_tokens`), which Sentry's AI dashboard
 * keys off.
 */
@Configuration
class AgentObservationConfig {
    @Bean
    fun genAiTokenUsageObservationFilter(): ObservationFilter =
        ObservationFilter { context ->
            if (context is ChatModelObservationContext) {
                context.response?.metadata?.usage?.let { usage ->
                    usage.promptTokens?.let {
                        context.addHighCardinalityKeyValue(KeyValue.of(INPUT_TOKENS, it.toString()))
                    }
                    usage.completionTokens?.let {
                        context.addHighCardinalityKeyValue(KeyValue.of(OUTPUT_TOKENS, it.toString()))
                    }
                    usage.totalTokens?.let {
                        context.addHighCardinalityKeyValue(KeyValue.of(TOTAL_TOKENS, it.toString()))
                    }
                }
            }
            context
        }

    companion object {
        private const val INPUT_TOKENS = "gen_ai.usage.input_tokens"
        private const val OUTPUT_TOKENS = "gen_ai.usage.output_tokens"
        private const val TOTAL_TOKENS = "gen_ai.usage.total_tokens"
    }
}