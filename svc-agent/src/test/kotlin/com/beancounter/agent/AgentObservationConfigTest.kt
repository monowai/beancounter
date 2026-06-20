package com.beancounter.agent

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
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
    private val filter = AgentObservationConfig().genAiTokenUsageObservationFilter()

    @Test
    fun `copies token usage from the chat response onto the span`() {
        val context =
            ChatModelObservationContext
                .builder()
                .prompt(Prompt("hi"))
                .provider("deepseek")
                .build()
        context.setResponse(
            ChatResponse(
                listOf(Generation(AssistantMessage("pong"))),
                ChatResponseMetadata.builder().usage(DefaultUsage(12, 7, 19)).build()
            )
        )

        filter.map(context)

        assertThat(context.highCardinalityKeyValues).contains(
            KeyValue.of("gen_ai.usage.input_tokens", "12"),
            KeyValue.of("gen_ai.usage.output_tokens", "7"),
            KeyValue.of("gen_ai.usage.total_tokens", "19")
        )
    }

    @Test
    fun `leaves a non-chat observation untouched`() {
        val context = Observation.Context()
        assertThat(filter.map(context)).isSameAs(context)
    }
}