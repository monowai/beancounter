package com.beancounter.agent

import com.beancounter.agent.clients.BodyRewrite
import com.beancounter.agent.clients.DeepSeekThinking
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicCacheOptions
import org.springframework.ai.anthropic.AnthropicCacheStrategy
import org.springframework.ai.anthropic.AnthropicCacheTtl
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.ai.deepseek.api.DeepSeekApi
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper

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
     * DeepSeek native ChatClient (thinking mode) for the interactive Chat FAB.
     * Uses Spring AI's first-class DeepSeek module rather than the OpenAI-compat
     * surface, so DeepSeekAssistantMessage.getReasoningContent() is available
     * and multi-turn tool calls correctly echo reasoning_content (DeepSeek 400s
     * otherwise).
     *
     * Hand-built rather than the autoconfigured model so every request carries
     * `reasoning_effort: low` — see [DeepSeekThinking.lowEffort]. Full-effort
     * thinking starved the answer of output budget on kauri (beancounter#1128);
     * a deep-think turn still sets `high` per call.
     */
    @Bean("chatClient")
    @Profile("deepseek")
    fun deepSeekChatClient(
        @Value($$"${spring.ai.deepseek.api-key:}") apiKey: String,
        @Value($$"${spring.ai.deepseek.base-url:https://api.deepseek.com}") baseUrl: String,
        @Value($$"${spring.ai.deepseek.chat.options.model:deepseek-flash}") model: String,
        @Value($$"${spring.ai.deepseek.chat.options.temperature:0.2}") temperature: Double,
        @Value($$"${spring.ai.deepseek.chat.options.max-tokens:4096}") maxTokens: Int,
        objectMapper: ObjectMapper,
        toolCallingManager: ToolCallingManager,
        observationRegistry: ObservationRegistry
    ): ChatClient {
        log.info("Building DeepSeek thinking ChatClient (low reasoning effort) for the Chat FAB")
        return build(
            deepSeekModel(
                DeepSeekConnection(apiKey, baseUrl, model, temperature, maxTokens),
                objectMapper,
                toolCallingManager,
                observationRegistry,
                DeepSeekThinking::lowEffort
            )
        )
    }

    /**
     * Non-thinking ("fast") DeepSeek ChatClient for pre-canned prompts.
     *
     * DeepSeek Flash defaults to thinking mode (big latency + reasoning-token
     * cost). Its requests carry `thinking: {type: disabled}` — see
     * [DeepSeekThinking.disableThinking]. [com.beancounter.agent.AgentController]
     * routes per request via the `think` flag.
     */
    @Bean("fastChatClient")
    @Profile("deepseek")
    fun fastDeepSeekChatClient(
        @Value($$"${spring.ai.deepseek.api-key:}") apiKey: String,
        @Value($$"${spring.ai.deepseek.base-url:https://api.deepseek.com}") baseUrl: String,
        @Value($$"${spring.ai.deepseek.chat.options.model:deepseek-flash}") model: String,
        @Value($$"${spring.ai.deepseek.chat.options.temperature:0.2}") temperature: Double,
        @Value($$"${spring.ai.deepseek.chat.options.max-tokens:4096}") maxTokens: Int,
        objectMapper: ObjectMapper,
        toolCallingManager: ToolCallingManager,
        observationRegistry: ObservationRegistry
    ): ChatClient {
        log.info("Building DeepSeek non-thinking (fast) ChatClient for pre-canned prompts")
        return build(
            deepSeekModel(
                DeepSeekConnection(apiKey, baseUrl, model, temperature, maxTokens),
                objectMapper,
                toolCallingManager,
                observationRegistry,
                DeepSeekThinking::disableThinking
            )
        )
    }

    /** DeepSeek endpoint and default chat options shared by both clients. */
    private data class DeepSeekConnection(
        val apiKey: String,
        val baseUrl: String,
        val model: String,
        val temperature: Double,
        val maxTokens: Int
    )

    /**
     * A DeepSeek model whose RestClient (sync) and WebClient (streaming) apply
     * [bodyRewrite] to every outgoing chat-completion request.
     */
    private fun deepSeekModel(
        connection: DeepSeekConnection,
        objectMapper: ObjectMapper,
        toolCallingManager: ToolCallingManager,
        observationRegistry: ObservationRegistry,
        bodyRewrite: BodyRewrite
    ): DeepSeekChatModel {
        val api =
            DeepSeekApi
                .builder()
                .apiKey(connection.apiKey)
                .baseUrl(connection.baseUrl)
                .restClientBuilder(
                    RestClient.builder().requestInterceptor(DeepSeekThinking.interceptor(objectMapper, bodyRewrite))
                ).webClientBuilder(
                    WebClient.builder().clientConnector(DeepSeekThinking.connector(objectMapper, bodyRewrite))
                ).build()
        val options =
            DeepSeekChatOptions
                .builder()
                .model(connection.model)
                .temperature(connection.temperature)
                .maxTokens(connection.maxTokens)
                .build()
        return DeepSeekChatModel
            .builder()
            .deepSeekApi(api)
            .options(options)
            .toolCallingManager(toolCallingManager)
            .observationRegistry(observationRegistry)
            .build()
    }

    /**
     * Anthropic prompt-cache config exposed as a bean so per-call code in
     * [AgentController] can re-include it when overriding model id.
     *
     * Cache the static prefix (system prompt + tool definitions) so the
     * multi-iteration tool-calling loop doesn't re-pay for ~4k tokens of
     * unchanged content on every round-trip. First request pays a 1.25×
     * cache-write premium; subsequent requests within the TTL pay 0.1× on
     * cache reads — a net ~40% input-token saving on any query that fires
     * more than one tool call.
     *
     * ONE_HOUR TTL suits sustained chat / scripted workloads hitting the
     * same system prompt; FIVE_MINUTES (Anthropic default) is cheaper on
     * the write but worse for ad-hoc traffic spread across an hour.
     */
    @Bean
    @Profile("!ollama & !openai & !deepseek")
    fun anthropicCacheOptions(): AnthropicCacheOptions =
        AnthropicCacheOptions
            .builder()
            .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
            .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
            .build()

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
    @Profile("!ollama & !openai & !deepseek")
    fun anthropicChatClient(
        chatModel: AnthropicChatModel,
        anthropicCacheOptions: AnthropicCacheOptions
    ): ChatClient {
        log.info(
            "Building Anthropic ChatClient ({}) with prompt caching enabled (default)",
            chatModel.javaClass.simpleName
        )

        // Spring AI 2.0: ChatClient.Builder.defaultOptions() now takes a
        // ChatOptions.Builder (not a built ChatOptions). Pass the builder.
        val anthropicOptions =
            AnthropicChatOptions
                .builder()
                .cacheOptions(anthropicCacheOptions)

        // No defaultSystem() — every call overrides via SystemPromptSelector
        // for domain-focused token usage. Fallback is DomainSystemPrompts.GENERAL.
        // ToolTurnBoundaryAdvisor: see build(model) below — this client builds
        // its own ChatClient rather than going through that helper, so it needs
        // the same registration.
        return ChatClient
            .builder(chatModel)
            .defaultOptions(anthropicOptions)
            .defaultSystem(DomainSystemPrompts.GENERAL)
            .defaultAdvisors(ToolTurnBoundaryAdvisor())
            .build()
    }

    private fun build(model: ChatModel): ChatClient =
        ChatClient
            .builder(model)
            .defaultSystem(DomainSystemPrompts.GENERAL)
            // ToolTurnBoundaryAdvisor re-marks a tool-calling turn's boundary
            // after Spring AI's ToolCallingAdvisor filters the real tool-call
            // element out of the stream — see its KDoc. Every non-Anthropic
            // client (ollama/openai/deepseek/fastChatClient) goes through this
            // helper, so registering it once here covers all of them.
            .defaultAdvisors(ToolTurnBoundaryAdvisor())
            .build()
}