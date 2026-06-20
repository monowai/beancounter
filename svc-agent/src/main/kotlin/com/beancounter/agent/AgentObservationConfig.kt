package com.beancounter.agent

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.ai.chat.observation.ChatModelObservationConvention
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Adds LLM token usage to the `gen_ai.client.operation` span.
 *
 * Spring AI records token counts only on the `gen_ai.client.token.usage`
 * **meter** — its default observation convention does not put usage on the span,
 * so the `chat <model>` span carried model/system/finish-reason but no tokens,
 * and Sentry's AI monitoring (and any cost baseline) had nothing to read.
 *
 * Overriding [DefaultChatModelObservationConvention.getHighCardinalityKeyValues]
 * is the supported extension point: the DeepSeek/Anthropic ChatModel
 * auto-configuration injects a [ChatModelObservationConvention] bean if present,
 * so these key-values are emitted on the **same path** that already lands
 * `gen_ai.response.model` / `finish_reasons` — i.e. guaranteed onto the span. A
 * generic `ObservationFilter` did not work: Spring AI's ChatModel observation
 * uses its configured convention, not the registry's filter chain.
 *
 * Names follow the OpenTelemetry GenAI semantic conventions
 * (`gen_ai.usage.input_tokens` / `output_tokens`), which Sentry's AI dashboard
 * keys off.
 */
@Configuration
class AgentObservationConfig {
    @Bean
    fun tokenUsageChatModelObservationConvention(): ChatModelObservationConvention =
        object : DefaultChatModelObservationConvention() {
            override fun getHighCardinalityKeyValues(context: ChatModelObservationContext): KeyValues {
                var keyValues = super.getHighCardinalityKeyValues(context)
                val usage = context.response?.metadata?.usage ?: return keyValues
                usage.promptTokens?.let {
                    keyValues = keyValues.and(KeyValue.of(INPUT_TOKENS, it.toString()))
                }
                usage.completionTokens?.let {
                    keyValues = keyValues.and(KeyValue.of(OUTPUT_TOKENS, it.toString()))
                }
                usage.totalTokens?.let {
                    keyValues = keyValues.and(KeyValue.of(TOTAL_TOKENS, it.toString()))
                }
                return keyValues
            }
        }

    companion object {
        private const val INPUT_TOKENS = "gen_ai.usage.input_tokens"
        private const val OUTPUT_TOKENS = "gen_ai.usage.output_tokens"
        private const val TOTAL_TOKENS = "gen_ai.usage.total_tokens"
    }
}