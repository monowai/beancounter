package com.beancounter.agent

import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicCacheOptions
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy
import org.springframework.ai.anthropic.api.AnthropicCacheTtl
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Wires a [ChatClient] from whichever [ChatModel] the active Spring profile
 * exposes, and registers all `@Tool`-bearing beans as default tools.
 *
 * We intentionally split by profile and inject the concrete subtype rather
 * than the `ChatModel` super-type with `@ConditionalOnBean`: Spring Boot
 * evaluates user-config conditions *before* auto-configuration runs, so a
 * condition looking for an auto-configured `ChatModel` bean will always see
 * nothing and the bean is silently skipped. Profile-qualified beans sidestep
 * that ordering trap — activation is driven by the profile, and the specific
 * subtype guarantees there is no ambiguity when both Ollama and OpenAI
 * starters are on the classpath.
 *
 * If neither profile is active, no `ChatClient` bean is created; the agent
 * starts anyway and /agent/query returns 503 "no-llm".
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@EnableConfigurationProperties(AgentModelTiers::class)
class ChatClientConfiguration {
    private val log = LoggerFactory.getLogger(ChatClientConfiguration::class.java)

    @Bean("chatClient")
    @Profile("ollama")
    fun ollamaChatClient(chatModel: OllamaChatModel): ChatClient {
        log.info("Building Ollama ChatClient ({})", chatModel.javaClass.simpleName)
        return build(chatModel)
    }

    @Bean("chatClient")
    @Profile("openai")
    fun openAiChatClient(chatModel: OpenAiChatModel): ChatClient {
        log.info("Building OpenAI ChatClient ({})", chatModel.javaClass.simpleName)
        return build(chatModel)
    }

    /**
     * Anthropic is the **default** ChatClient — created whenever neither
     * `ollama` nor `openai` is in the active profile list. This matches the
     * agent's `application.yml` defaults (`spring.ai.model.chat: anthropic`)
     * so a developer running with just the `kauri` (or no) profile gets a
     * working Claude-backed agent without having to remember to also add
     * `,anthropic` to `SPRING_PROFILES_ACTIVE`. Activating `ollama` or
     * `openai` explicitly disables this fallback.
     */
    @Bean("chatClient")
    @Profile("!ollama & !openai")
    fun anthropicChatClient(chatModel: AnthropicChatModel): ChatClient {
        log.info(
            "Building Anthropic ChatClient ({}) with prompt caching enabled (default)",
            chatModel.javaClass.simpleName
        )

        // Enable Anthropic prompt caching for the static prefix (system
        // prompt + tool definitions) so the multi-iteration tool-calling
        // loop doesn't re-pay for ~4k tokens of unchanged content on every
        // round-trip. First request pays a 1.25× cache-write premium;
        // subsequent requests within the TTL pay 0.1× on cache reads — a
        // net ~40% input-token saving on any query that fires more than
        // one tool call.
        //
        // FIVE_MINUTES TTL matches Anthropic's default and suits ad-hoc
        // chat traffic. Swap to ONE_HOUR if you have sustained scripted
        // workloads hitting the same system prompt; it's more expensive
        // on the write but cheaper over a long session.
        val cacheOptions =
            AnthropicCacheOptions
                .builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
                .build()

        val anthropicOptions =
            AnthropicChatOptions
                .builder()
                .cacheOptions(cacheOptions)
                .build()

        // No defaultSystem() — every call overrides via SystemPromptSelector
        // for domain-focused token usage. Fallback is DomainSystemPrompts.GENERAL.
        return ChatClient
            .builder(chatModel)
            .defaultOptions(anthropicOptions)
            .defaultSystem(DomainSystemPrompts.GENERAL)
            .build()
    }

    private fun build(model: ChatModel): ChatClient =
        ChatClient
            .builder(model)
            .defaultSystem(DomainSystemPrompts.GENERAL)
            .build()
}