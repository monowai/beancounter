package com.beancounter.agent

import com.beancounter.agent.clients.DeepSeekNonThinking
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
     * DeepSeek native ChatClient. Uses Spring AI's first-class DeepSeek module
     * (spring-ai-starter-model-deepseek) rather than the OpenAI-compat surface,
     * so DeepSeekAssistantMessage.getReasoningContent() is available and
     * multi-turn tool calls correctly strip reasoning_content from echoed
     * messages (DeepSeek 400s otherwise).
     */
    @Bean("chatClient")
    @Profile("deepseek")
    fun deepSeekChatClient(chatModel: DeepSeekChatModel): ChatClient {
        log.info("Building DeepSeek ChatClient ({})", chatModel.javaClass.simpleName)
        return build(chatModel)
    }

    /**
     * Non-thinking ("fast") DeepSeek ChatClient for pre-canned prompts.
     *
     * DeepSeek v4-flash defaults to thinking mode (big latency + reasoning-token
     * cost). Spring AI 2.0 exposes no option to disable it, so this builds a
     * second DeepSeek model whose RestClient (sync) + WebClient (streaming) inject
     * `thinking: {type: disabled}` into the request body — see [DeepSeekNonThinking].
     * The default [deepSeekChatClient] keeps thinking on for the interactive Chat
     * FAB; [com.beancounter.agent.AgentController] routes per request via the
     * `think` flag.
     */
    @Bean("fastChatClient")
    @Profile("deepseek")
    fun fastDeepSeekChatClient(
        @Value($$"${spring.ai.deepseek.api-key:}") apiKey: String,
        @Value($$"${spring.ai.deepseek.base-url:https://api.deepseek.com}") baseUrl: String,
        @Value($$"${spring.ai.deepseek.chat.options.model:deepseek-v4-flash}") model: String,
        @Value($$"${spring.ai.deepseek.chat.options.temperature:0.2}") temperature: Double,
        @Value($$"${spring.ai.deepseek.chat.options.max-tokens:4096}") maxTokens: Int,
        objectMapper: ObjectMapper,
        toolCallingManager: ToolCallingManager,
        observationRegistry: ObservationRegistry
    ): ChatClient {
        log.info("Building DeepSeek non-thinking (fast) ChatClient for pre-canned prompts")
        val fastApi =
            DeepSeekApi
                .builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .restClientBuilder(
                    RestClient.builder().requestInterceptor(DeepSeekNonThinking.interceptor(objectMapper))
                ).webClientBuilder(
                    WebClient.builder().clientConnector(DeepSeekNonThinking.connector(objectMapper))
                ).build()
        val options =
            DeepSeekChatOptions
                .builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build()
        val fastModel =
            DeepSeekChatModel
                .builder()
                .deepSeekApi(fastApi)
                .options(options)
                .toolCallingManager(toolCallingManager)
                .observationRegistry(observationRegistry)
                .build()
        return build(fastModel)
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