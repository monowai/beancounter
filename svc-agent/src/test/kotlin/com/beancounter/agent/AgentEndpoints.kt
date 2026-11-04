package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.conversation.TurnLog
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.holding.HoldingAnalysisOpening
import com.beancounter.agent.holding.HoldingFactsSource
import com.beancounter.agent.quota.TurnQuota
import com.beancounter.agent.review.AssetReviewCache
import com.beancounter.agent.tools.ToolSelector
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/** Both transports of the agent, as a test calls them. */
class AgentEndpoints(
    private val queries: AgentController,
    private val streams: AgentStreamController
) {
    fun health() = queries.health()

    fun query(body: AgentQuery) = queries.query(body)

    fun stream(body: AgentQuery) = streams.stream(body)
}

/**
 * The one place tests build the agent's controllers by hand. Every collaborator
 * defaults to the inert test double: no tools, a fixed prompt and model, a
 * verified caller, and nothing cached, held or metered.
 */
fun agentEndpoints(
    chatClient: ChatClient? = null,
    fastChatClient: ChatClient? = null,
    healthChecker: ServiceHealthChecker = mock(),
    toolSelector: ToolSelector = mock { on { selectTools(anyOrNull()) } doAnswer { arrayOf() } },
    systemPromptSelector: SystemPromptSelector = mock { on { selectFor(anyOrNull()) } doAnswer { "prompt" } },
    chatModelSelector: ChatModelSelector = mock { on { selectFor(anyOrNull(), any()) } doAnswer { "model" } },
    environment: Environment = MockEnvironment(),
    scopeAuthorizer: AgentScopeAuthorizer = mock { on { verified() } doAnswer { true } },
    conversations: TurnLog = mock(),
    conversationOwner: ConversationOwner = mock(),
    conversationTitler: ConversationTitler = mock(),
    inFlightTurns: InFlightTurns = InFlightTurns(),
    clock: Clock = Clock.systemUTC(),
    pageQuerySelector: PageQuerySelector = PageQuerySelector(),
    reviewCache: AssetReviewCache = AssetReviewCache.NONE,
    holdings: HoldingFactsSource = HoldingFactsSource.NONE,
    quota: TurnQuota = TurnQuota.NONE
): AgentEndpoints {
    val routing = ModelRouting(chatClient, fastChatClient, null, chatModelSelector, environment)
    val metrics = LlmMetrics()
    val planner = TurnPlanner(pageQuerySelector, conversations, conversationOwner, reviewCache, routing, clock)
    val metering = TurnMetering(scopeAuthorizer, conversationOwner, quota)
    val prompts = TurnPrompts(toolSelector, systemPromptSelector, routing, metrics, clock)
    val turns = ConversationTurns(conversations, conversationTitler, pageQuerySelector, reviewCache, metering)
    val opening = HoldingAnalysisOpening(holdings, planner, prompts, reviewCache)
    val admission = TurnAdmission(planner, metering, routing)
    return AgentEndpoints(
        AgentController(
            routing,
            healthChecker,
            scopeAuthorizer,
            admission,
            BlockingAnswers(prompts, metrics, turns, opening)
        ),
        AgentStreamController(
            scopeAuthorizer,
            admission,
            AnswerStreams(prompts, routing, metrics, ObjectMapper(), turns, opening),
            turns,
            inFlightTurns
        )
    )
}