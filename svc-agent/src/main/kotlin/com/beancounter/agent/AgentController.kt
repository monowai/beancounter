package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.conversation.TurnRecorder
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.holding.HoldingFactsSource
import com.beancounter.agent.review.AssetReviewCache
import com.beancounter.agent.review.AssetReviewKey
import com.beancounter.agent.review.ReviewCachingRecorder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Single natural-language entry point for the Beancounter agent.
 *
 * The previous incarnation of this controller had ~10 endpoints and a 25-case
 * dispatcher in a `BeancounterAgent` service that hand-mapped queries to
 * actions. All of that is gone: the LLM now drives tool selection through
 * Spring AI's `@Tool` calling, so this controller is just a thin pass-through.
 * The streaming variant of `/query` is [AgentStreamController].
 */
@RestController
@RequestMapping("/agent")
@Tag(name = "Agent", description = "Natural-language Beancounter assistant")
class AgentController(
    private val routing: ModelRouting,
    private val healthChecker: ServiceHealthChecker,
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val admission: TurnAdmission,
    private val answers: BlockingAnswers
) {
    private companion object {
        // Escapes markup only: a label's dash is not an entity.
        const val UTF_8 = "UTF-8"
    }

    @GetMapping("/health")
    @Operation(
        summary = "Traffic-light health check for the agent and its downstream services.",
        description =
            "Pings actuator/health on bc-data, bc-position and bc-event, and reports " +
                "whether a Spring AI ChatClient is configured. Unauthenticated."
    )
    fun health(): AgentHealthResponse = healthChecker.check(llmAvailable = routing.available)

    @PostMapping("/query")
    @Operation(
        summary = "Ask the agent a natural language question",
        description =
            "The LLM is given a small fixed set of tools that call the standard " +
                "Beancounter REST APIs. It chooses which to invoke. A blank query asks " +
                "the canned question for `context.page` (Asset Review, Portfolio Review)."
    )
    fun query(
        @RequestBody body: AgentQuery
    ): ResponseEntity<AgentResponse> {
        scopeAuthorizer.authorize()
        return when (val admission = admission.admit(body)) {
            is Admission.NoLlm -> answers.noLlm(admission.request)
            is Admission.Refused -> answers.failure(HtmlUtils.htmlEscape(admission.label, UTF_8), admission.code)
            is Admission.Admitted -> answers.answer(admission)
        }
    }
}

data class AgentQuery(
    /**
     * The question. Blank asks the page's canned question instead
     * ([PageQuerySelector]); a page without one rejects a blank query.
     */
    val query: String = "",
    val context: Map<String, Any>? = null,
    /**
     * Enable DeepSeek thinking mode for this request. Defaults to `false` so
     * pre-canned prompts get the fastest (non-thinking) response; the interactive
     * Chat FAB sets `true`. Orthogonal to [deepThink] (which escalates the model
     * tier). When `false`, a SMART page ([ChatModelSelector.reasonsFor]) still
     * reasons at low effort; any other page routes to the non-thinking `fastChatClient`.
     */
    val think: Boolean = false,
    /**
     * Caller-driven escalation to the deep tier (typically `deepseek-v4-pro`
     * or `claude-opus-*`). Off by default; flips model selection regardless of
     * page-context routing in [ChatModelSelector].
     */
    val deepThink: Boolean = false,
    /**
     * Prior conversation turns, oldest first, so the model can see its own
     * previous question when the user replies to it instead of retyping the
     * whole enriched question every round. Caller-supplied and scoped to
     * this request only — no server-side persistence. Truncated server-side
     * to the trailing few turns regardless of length.
     */
    val history: List<ChatTurn>? = null,
    /**
     * The saved conversation this turn belongs to (`POST /agent/conversations`
     * mints one). When set, the server replays that conversation's stored
     * turns in place of [history] and records this question and its answer.
     */
    val conversationId: String? = null,
    /**
     * Display text for a canned prompt (a Quick Analysis), stored beside the
     * question so a reloaded conversation shows it instead of the prompt.
     */
    val label: String? = null,
    /**
     * Ask the model even when a cached answer to this canned review exists,
     * and keep the new answer in its place.
     */
    val refresh: Boolean = false
)

/**
 * One turn of caller-supplied conversation history. [role] is `"user"` or
 * `"assistant"`; anything else is treated as `"user"`.
 */
data class ChatTurn(
    val role: String,
    val content: String
)

data class AgentResponse(
    val query: String,
    val response: String,
    val timestamp: String,
    val error: String? = null,
    /** True when the answer was served from the Asset Review cache, not the model. */
    val cached: Boolean = false
)