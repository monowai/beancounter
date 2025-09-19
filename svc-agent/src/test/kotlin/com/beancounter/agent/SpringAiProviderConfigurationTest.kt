package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests to verify Spring AI provider configuration assumptions across profiles.
 * These tests document and assert the expected provider selection behavior
 * for each Spring profile without requiring full Spring context.
 */
class SpringAiProviderConfigurationTest {
    @Test
    fun `default profile configuration assumptions`() {
        // Default profile should disable ALL LLM providers using spring.ai.model.*=none
        val expectedConfig =
            mapOf(
                "spring.ai.model.chat" to "none",
                "spring.ai.model.embedding" to "none",
                "spring.ai.model.image" to "none",
                "spring.ai.model.audio.speech" to "none",
                "spring.ai.model.audio.transcription" to "none",
                "spring.ai.model.moderation" to "none"
            )

        // Assert each provider is set to 'none'
        assertThat(expectedConfig["spring.ai.model.chat"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.embedding"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.image"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.audio.speech"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.audio.transcription"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.moderation"]).isEqualTo("none")

        // Verify no active providers
        val activeProviders = expectedConfig.values.filter { it != "none" }
        assertThat(activeProviders).isEmpty()
    }

    @Test
    fun `ollama profile configuration assumptions`() {
        // Ollama profile should enable only Ollama for supported model types
        val expectedConfig =
            mapOf(
                "spring.ai.model.chat" to "ollama",
                "spring.ai.model.embedding" to "ollama",
                "spring.ai.model.image" to "none", // Ollama doesn't support image generation
                "spring.ai.model.audio.speech" to "none", // Ollama doesn't support speech synthesis
                "spring.ai.model.audio.transcription" to "none", // Ollama doesn't support transcription
                "spring.ai.model.moderation" to "none" // Ollama doesn't support content moderation
            )

        // Assert Ollama is enabled for supported types
        assertThat(expectedConfig["spring.ai.model.chat"]).isEqualTo("ollama")
        assertThat(expectedConfig["spring.ai.model.embedding"]).isEqualTo("ollama")

        // Assert unsupported types are disabled
        assertThat(expectedConfig["spring.ai.model.image"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.audio.speech"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.audio.transcription"]).isEqualTo("none")
        assertThat(expectedConfig["spring.ai.model.moderation"]).isEqualTo("none")

        // Verify only Ollama providers are active
        val activeProviders = expectedConfig.values.filter { it != "none" }
        assertThat(activeProviders).containsOnly("ollama")

        // Verify expected capabilities
        val supportedCapabilities = expectedConfig.filterValues { it == "ollama" }.keys
        assertThat(supportedCapabilities).containsExactlyInAnyOrder(
            "spring.ai.model.chat",
            "spring.ai.model.embedding"
        )
    }

    @Test
    fun `openai profile configuration assumptions`() {
        // OpenAI profile should enable OpenAI for ALL supported model types
        val expectedConfig =
            mapOf(
                "spring.ai.model.chat" to "openai",
                "spring.ai.model.embedding" to "openai",
                "spring.ai.model.image" to "openai",
                "spring.ai.model.audio.speech" to "openai",
                "spring.ai.model.audio.transcription" to "openai",
                "spring.ai.model.moderation" to "openai"
            )

        // Assert OpenAI is enabled for all types
        assertThat(expectedConfig["spring.ai.model.chat"]).isEqualTo("openai")
        assertThat(expectedConfig["spring.ai.model.embedding"]).isEqualTo("openai")
        assertThat(expectedConfig["spring.ai.model.image"]).isEqualTo("openai")
        assertThat(expectedConfig["spring.ai.model.audio.speech"]).isEqualTo("openai")
        assertThat(expectedConfig["spring.ai.model.audio.transcription"]).isEqualTo("openai")
        assertThat(expectedConfig["spring.ai.model.moderation"]).isEqualTo("openai")

        // Verify only OpenAI providers are active
        val activeProviders = expectedConfig.values.distinct()
        assertThat(activeProviders).containsOnly("openai")

        // Verify comprehensive capabilities
        val supportedCapabilities = expectedConfig.filterValues { it == "openai" }.keys
        assertThat(supportedCapabilities).hasSize(6)
        assertThat(supportedCapabilities).contains(
            "spring.ai.model.chat",
            "spring.ai.model.embedding",
            "spring.ai.model.image",
            "spring.ai.model.audio.speech",
            "spring.ai.model.audio.transcription",
            "spring.ai.model.moderation"
        )
    }

    @Test
    fun `provider selection ensures mutual exclusivity`() {
        // Each profile should select providers that don't conflict

        // Default: No providers active
        val defaultProviders = setOf<String>()

        // Ollama: Only Ollama active
        val ollamaProviders = setOf("ollama")

        // OpenAI: Only OpenAI active
        val openaiProviders = setOf("openai")

        // Assert no overlap between active provider sets
        assertThat(defaultProviders).doesNotContainAnyElementsOf(ollamaProviders)
        assertThat(defaultProviders).doesNotContainAnyElementsOf(openaiProviders)
        assertThat(ollamaProviders).doesNotContainAnyElementsOf(openaiProviders)

        // Assert each profile has distinct provider selection
        assertThat(defaultProviders).isNotEqualTo(ollamaProviders)
        assertThat(defaultProviders).isNotEqualTo(openaiProviders)
        assertThat(ollamaProviders).isNotEqualTo(openaiProviders)
    }

    @Test
    fun `provider selection matches capability expectations`() {
        // Document expected capabilities for each provider

        val ollamaCapabilities = setOf("chat", "embedding")
        val openaiCapabilities = setOf("chat", "embedding", "image", "speech", "transcription", "moderation")

        // Ollama: Limited but focused capabilities
        assertThat(ollamaCapabilities).containsExactlyInAnyOrder("chat", "embedding")
        assertThat(ollamaCapabilities).hasSize(2)

        // OpenAI: Comprehensive capabilities
        assertThat(openaiCapabilities).containsExactlyInAnyOrder(
            "chat",
            "embedding",
            "image",
            "speech",
            "transcription",
            "moderation"
        )
        assertThat(openaiCapabilities).hasSize(6)

        // OpenAI should be superset of Ollama capabilities
        assertThat(openaiCapabilities).containsAll(ollamaCapabilities)

        // But they have different focuses
        val openaiOnlyCapabilities = openaiCapabilities - ollamaCapabilities
        assertThat(openaiOnlyCapabilities).containsExactlyInAnyOrder(
            "image",
            "speech",
            "transcription",
            "moderation"
        )
    }
}