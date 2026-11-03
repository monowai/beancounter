package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationService
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.health.ServiceHealthChecker
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

/**
 * The one place tests build an [AgentController] by hand. Every collaborator
 * defaults to the inert test double: no tools, a fixed prompt and model, a
 * verified caller, and nothing cached, held or metered.
 */
fun agentController(
    chatClient: ChatClient? = null,
    fastChatClient: ChatClient? = null,
    healthChecker: ServiceHealthChecker = mock(),
    toolSelector: ToolSelector = mock { on { selectTools(anyOrNull()) } doAnswer { arrayOf() } },
    systemPromptSelector: SystemPromptSelector = mock { on { selectFor(anyOrNull()) } doAnswer { "prompt" } },
    chatModelSelector: ChatModelSelector = mock { on { selectFor(anyOrNull(), any()) } doAnswer { "model" } },
    environment: Environment = MockEnvironment(),
    scopeAuthorizer: AgentScopeAuthorizer = mock { on { verified() } doAnswer { true } },
    conversations: ConversationService = mock(),
    conversationOwner: ConversationOwner = mock(),
    conversationTitler: ConversationTitler = mock(),
    inFlightTurns: InFlightTurns = InFlightTurns(),
    clock: Clock = Clock.systemUTC(),
    pageQuerySelector: PageQuerySelector = PageQuerySelector(),
    reviewCache: AssetReviewCache = AssetReviewCache.NONE,
    holdings: HoldingFactsSource = HoldingFactsSource.NONE,
    quota: TurnQuota = TurnQuota.NONE
): AgentController =
    AgentController(
        routing = ModelRouting(chatClient, fastChatClient, null, chatModelSelector, environment),
        healthChecker = healthChecker,
        toolSelector = toolSelector,
        systemPromptSelector = systemPromptSelector,
        objectMapper = ObjectMapper(),
        llmMetrics = LlmMetrics(),
        scopeAuthorizer = scopeAuthorizer,
        conversations = conversations,
        conversationOwner = conversationOwner,
        conversationTitler = conversationTitler,
        inFlightTurns = inFlightTurns,
        clock = clock,
        pageQuerySelector = pageQuerySelector,
        reviewCache = reviewCache,
        holdings = holdings,
        quota = quota
    )