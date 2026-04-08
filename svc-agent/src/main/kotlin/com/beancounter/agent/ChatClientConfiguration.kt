package com.beancounter.agent

import com.beancounter.agent.tools.EventTools
import com.beancounter.agent.tools.MarketTools
import com.beancounter.agent.tools.PortfolioTools
import com.beancounter.agent.tools.PositionTools
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
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
@Configuration
class ChatClientConfiguration {
    private val log = LoggerFactory.getLogger(ChatClientConfiguration::class.java)

    @Bean("chatClient")
    @Profile("ollama")
    fun ollamaChatClient(
        chatModel: OllamaChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools
    ): ChatClient {
        log.info("Building Ollama ChatClient ({})", chatModel.javaClass.simpleName)
        return build(chatModel, portfolioTools, positionTools, eventTools, marketTools)
    }

    @Bean("chatClient")
    @Profile("openai")
    fun openAiChatClient(
        chatModel: OpenAiChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools
    ): ChatClient {
        log.info("Building OpenAI ChatClient ({})", chatModel.javaClass.simpleName)
        return build(chatModel, portfolioTools, positionTools, eventTools, marketTools)
    }

    private fun build(
        model: ChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools
    ): ChatClient =
        ChatClient
            .builder(model)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(portfolioTools, positionTools, eventTools, marketTools)
            .build()

    companion object {
        private val SYSTEM_PROMPT =
            """
            You are the Beancounter portfolio assistant. Use the provided tools to answer
            questions about portfolios, positions, valuations, FX rates, markets, currencies
            and corporate events. Always look up data through tools rather than guessing.

            Users identify portfolios by short codes (e.g. "TYLER", "NZD", "MAIN"). Every
            tool that touches a portfolio takes a portfolio code directly — never ask the
            user for an id, and never invent one. If a portfolio code is not found, ask
            the user to check the spelling or call listPortfolios to show what's available.

            Reply in clear markdown.
            """.trimIndent()
    }
}