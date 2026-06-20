package com.beancounter.agent

import io.micrometer.common.KeyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.ai.chat.prompt.Prompt

class AgentObservationConfigTest {
    private val convention = AgentObservationConfig().tokenUsageChatModelObservationConvention()

    private fun contextWithUsage(usage: DefaultUsage): ChatModelObservationContext {
        val context =
            ChatModelObservationContext
                .builder()
                .prompt(Prompt("hi"))
                .provider("deepseek")
                .build()
        context.setResponse(
            ChatResponse(
                listOf(Generation(AssistantMessage("pong"))),
                ChatResponseMetadata.builder().usage(usage).build()
            )
        )
        return context
    }

    @Test
    fun `emits gen_ai usage key-values from the chat response`() {
        val keyValues = convention.getHighCardinalityKeyValues(contextWithUsage(DefaultUsage(12, 7, 19)))

        assertThat(keyValues).contains(
            KeyValue.of("gen_ai.usage.input_tokens", "12"),
            KeyValue.of("gen_ai.usage.output_tokens", "7"),
            KeyValue.of("gen_ai.usage.total_tokens", "19")
        )
    }

    @Test
    fun `keeps the default high-cardinality key-values`() {
        // The override must augment, not replace, the Spring AI defaults (e.g.
        // gen_ai.response.finish_reasons), so existing span attributes survive.
        val keyValues = convention.getHighCardinalityKeyValues(contextWithUsage(DefaultUsage(1, 1, 2)))

        assertThat(keyValues.map { it.key }).contains("gen_ai.usage.total_tokens")
        assertThat(keyValues.stream().count()).isGreaterThan(1)
    }
}