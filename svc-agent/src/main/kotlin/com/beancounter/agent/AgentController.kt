package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationKind
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationRecorder
import com.beancounter.agent.conversation.ConversationService
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.conversation.TurnRecorder
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.holding.HoldingFactsSource
import com.beancounter.agent.quota.QuotaRecorder
import com.beancounter.agent.quota.TurnQuota
import com.beancounter.agent.review.AssetReviewCache
import com.beancounter.agent.review.AssetReviewKey
import com.beancounter.agent.review.ReviewCachingRecorder
import com.beancounter.agent.tools.ToolSelector
import com.beancounter.common.exception.BusinessException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicCacheOptions
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
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
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Single natural-language entry point for the Beancounter agent.
 *
 * The previous incarnation of this controller had ~10 endpoints and a 25-case
 * dispatcher in a `BeancounterAgent` service that hand-mapped queries to
 * actions. All of that is gone: the LLM now drives tool selection through
 * Spring AI's `@Tool` calling, so this controller is just a thin pass-through.
 */
@RestController
@RequestMapping("/agent")
@Tag(name = "Agent", description = "Natural-language Beancounter assistant")
class AgentController(
    @Autowired(required = false) private val chatClient: ChatClient?,
    @Autowired(required = false)
    @Qualifier("fastChatClient")
    private val fastChatClient: ChatClient? = null,
    @Autowired(required = false) private val anthropicCacheOptions: AnthropicCacheOptions?,
    private val healthChecker: ServiceHealthChecker,
    private val toolSelector: ToolSelector,
    private val systemPromptSelector: SystemPromptSelector,
    private val chatModelSelector: ChatModelSelector,
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
    private val llmMetrics: LlmMetrics,
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val conversations: ConversationService,
    private val conversationOwner: ConversationOwner,
    private val conversationTitler: ConversationTitler,
    private val inFlightTurns: InFlightTurns,
    // UTC clock for stamping the current date onto each user message. Defaulted so Spring wires it
    // without a Clock bean; overridden in tests for a fixed date.
    private val clock: Clock = Clock.systemUTC(),
    // Defaulted like the clock, so the tests that build the controller by hand need not pass it.
    private val pageQuerySelector: PageQuerySelector = PageQuerySelector(),
    // Defaulted like the clock: the hand-built controllers in tests cache nothing.
    private val reviewCache: AssetReviewCache = AssetReviewCache.NONE,
    // Defaulted like the clock: the hand-built controllers in tests hold nothing.
    private val holdings: HoldingFactsSource = HoldingFactsSource.NONE,
    // Defaulted like the clock: the hand-built controllers in tests meter nothing.
    private val quota: TurnQuota = TurnQuota.NONE
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

    private companion object {
        // Two retries with exponential backoff (+jitter) cover the common
        // sub-second connectivity blip without holding the request open long
        // enough to outlast a real outage — that surfaces as the error envelope.
        const val STREAM_RETRY_ATTEMPTS = 2L
        val STREAM_RETRY_MIN_BACKOFF: Duration = Duration.ofMillis(250)

        // Caller-supplied history is trusted only up to this many trailing
        // turns — bounds the tokens a single request can push into the
        // prompt regardless of what the client sends.
        const val MAX_HISTORY_TURNS = 6

        // DeepSeek output caps. In thinking mode `max_tokens` covers
        // reasoning_content plus the answer, so a thinking turn needs the
        // deep-tier headroom; the non-thinking fast client keeps the small cap.
        const val FAST_MAX_TOKENS = 4096
        const val THINKING_MAX_TOKENS = 16384

        // Stable client-facing failure codes. bc-view keys its copy off these
        // (see lib/utils/agent/agentErrors.ts); renaming one is a contract change.
        // The set: no-llm, provider-quota, provider-rate, provider-timeout,
        // agent-error, answer-truncated, empty-answer, not-held, ai-quota,
        // email-unverified, ai-disabled.
        const val NO_LLM = "no-llm"

        /** The caller has used this week's AI turns ([com.beancounter.agent.quota.AgentQuota]). */
        const val AI_QUOTA = "ai-quota"

        /** An administrator has turned AI off for the caller (a weekly limit of 0). */
        const val AI_DISABLED = "ai-disabled"

        /** The caller has not confirmed their email address, so is served cached Asset Reviews only. */
        const val EMAIL_UNVERIFIED = "email-unverified"

        const val QUERY_REQUIRED = "A query is required for this page"

        // Escapes markup only: a label's dash is not an entity.
        const val UTF_8 = "UTF-8"
        const val PROVIDER_QUOTA = "provider-quota"
        const val PROVIDER_RATE = "provider-rate"
        const val PROVIDER_TIMEOUT = "provider-timeout"
        const val AGENT_ERROR = "agent-error"

        /**
         * A Holding Analysis of an asset the portfolio does not hold, or of a
         * portfolio the caller cannot see — one code, so neither is revealed.
         */
        const val NOT_HELD = "not-held"

        /**
         * The turn ended on a token cap with nothing to show for it — on the
         * Independence domain this is the context window filling with tool
         * results (a full year-by-year projection runs to tens of thousands of
         * tokens), leaving no output budget for the answer itself.
         */
        const val ANSWER_TRUNCATED = "answer-truncated"

        /** The turn ended normally but produced no answer text. */
        const val EMPTY_ANSWER = "empty-answer"

        // Finish reasons that mean "stopped because it ran out of room", as
        // opposed to "finished what it had to say". DeepSeek/OpenAI say
        // "length"; Anthropic says "max_tokens".
        val TRUNCATION_FINISH_REASONS = setOf("length", "max_tokens")

        // Leading chars of the user's question kept in a log line — enough to
        // identify the request in kauri's logs without shipping the whole
        // question (which can carry page context the user typed).
        const val QUERY_LOG_CHARS = 120

        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val PAYMENT_REQUIRED = 402
        const val TOO_MANY_REQUESTS = 429
        const val INTERNAL_ERROR = 500
        const val UNPROCESSABLE_CONTENT = 422
        const val GATEWAY_TIMEOUT = 504

        // Only a status at the very start of the message is trusted — that is
        // where Spring AI's "<status> - <body>" puts it. Digit boundaries stop
        // a number inside a tool result from reading as a status.
        val LEADING_HTTP_STATUS = Regex("""^\s*(\d{3})(?!\d)""")
        const val CAUSE_CHAIN_DEPTH = 5

        // Finish reasons that mark a turn as narration-before-tool-calls,
        // lower-cased for case-insensitive matching in sseEventsFor. The
        // production source is ToolTurnBoundaryAdvisor's synthetic element
        // (via the shared BOUNDARY_FINISH_REASON constant); the provider wire
        // values ("tool_calls" DeepSeek/OpenAI, "tool_use" from Anthropic's
        // StopReason.toString()) stay listed in case a Spring AI change ever
        // lets a real one through. Do NOT add "pause_turn" or any other
        // Anthropic stop reason here.
        val TOOL_TURN_FINISH_REASONS = setOf(ToolTurnBoundaryAdvisor.BOUNDARY_FINISH_REASON, "tool_use")

        // Every other finish reason a turn can legitimately end with — the
        // final answer. DeepSeek/OpenAI: "stop", "length". Anthropic:
        // "end_turn", "stop_sequence", "max_tokens", "pause_turn". Anything
        // outside both this set and TOOL_TURN_FINISH_REASONS is unrecognized
        // (e.g. a provider/Spring AI version bump changing the wire value)
        // and gets logged rather than silently dropped.
        val KNOWN_FINAL_FINISH_REASONS =
            setOf("stop", "end_turn", "stop_sequence", "max_tokens", "length", "pause_turn")
    }

    /**
     * The domain prompt for the request's page, plus [DomainSystemPrompts.FOLLOW_UP]
     * when the caller sent history — so a question about an earlier answer is
     * answered rather than met with a regenerated brief.
     */
    internal fun systemPromptFor(request: AgentQuery): String {
        val domain = systemPromptSelector.selectFor(request.context)
        return if (request.history.isNullOrEmpty()) domain else "$domain\n\n${DomainSystemPrompts.FOLLOW_UP}"
    }

    /**
     * The question this turn asks: the caller's own when it sent one, else the
     * page's canned question ([PageQuerySelector]), stored under the page's name
     * when the caller gave no label. A page with no canned question needs the
     * caller to ask something — a 400 otherwise.
     */
    internal fun withQuestion(request: AgentQuery): AgentQuery {
        if (request.query.isNotBlank()) return request
        val canned =
            pageQuerySelector.queryFor(request.context)
                ?: throw BusinessException(QUERY_REQUIRED)
        return request.copy(query = canned.question, label = request.label ?: canned.label)
    }

    /**
     * Resolve the request's effective history and where its turn is recorded.
     * A request naming a conversation replays that conversation's stored turns
     * — client-sent history is ignored, so a caller cannot put words in the
     * assistant's mouth — and records this question to it now. Any other
     * request is stateless. The conversation was resolved by [threadOf] on
     * the request thread, where the owner lookup has the caller's token and a
     * conversation the caller doesn't own surfaces as a 404.
     */
    private fun openTurn(
        request: AgentQuery,
        thread: ConversationThread?
    ): Pair<AgentQuery, TurnRecorder> {
        if (thread == null) return request to TurnRecorder.NONE
        val ownerId = thread.ownerId
        val conversationId = thread.id
        val stored = conversations.history(ownerId, conversationId, MAX_HISTORY_TURNS)
        conversations.appendUser(
            ownerId,
            conversationId,
            request.query,
            request.deepThink,
            request.label,
            pageQuerySelector.kindFor(request.context)
        )
        // Titles see a canned prompt's label, which names the subject without its instructions.
        val recorder =
            ConversationRecorder(
                service = conversations,
                titler = conversationTitler,
                ownerId = ownerId,
                conversationId = conversationId,
                // A hidden conversation is never listed, so a title would be an LLM call for nothing.
                awaitingTitle =
                    stored.none { it.role == ConversationService.ROLE_ASSISTANT } &&
                        !conversations.isHidden(ownerId, conversationId),
                provisionalTitle = conversations.titleOf(ownerId, conversationId),
                questions =
                    stored.filter { it.role == ConversationService.ROLE_USER }.map { it.content } +
                        (request.label ?: request.query)
            )
        return request.copy(history = stored) to recorder
    }

    /** A Holding Analysis's opening turn, ready to ask, or the stable code it failed with. */
    private sealed interface Opening {
        data class Ready(
            val turn: AgentQuery
        ) : Opening

        data class Failed(
            val code: String
        ) : Opening
    }

    /** A turn that could not be answered, carrying the stable code it fails with. */
    private class UnansweredTurn(
        val code: String
    ) : RuntimeException(code)

    /** The caller's conversation a request names: its owner, and what it already is. */
    private data class ConversationThread(
        val ownerId: String,
        val id: String,
        val kind: ConversationKind,
        val hasTurns: Boolean
    )

    /**
     * How one request is answered: [request] is the turn to ask, or, when
     * [opening] is set, the start of a Holding Analysis whose question is
     * built from that subject by [holdingOpening].
     */
    private data class Plan(
        val request: AgentQuery,
        val opening: HoldingAnalysisSubject?,
        val thread: ConversationThread?
    )

    /** The conversation [body] names, resolved for the caller, or null for a stateless request. */
    private fun threadOf(body: AgentQuery): ConversationThread? {
        val id = body.conversationId ?: return null
        val ownerId = conversationOwner.id()
        val state = conversations.stateOf(ownerId, id)
        return ConversationThread(ownerId, id, state.kind, state.hasTurns)
    }

    /**
     * Plan [body]. A Holding Analysis is routed by its conversation as much
     * as by its page: a turn in a Holding Analysis conversation that has begun
     * is a follow-up, answered with the Holding Analysis prompt and tools
     * whatever page it names, and asks the caller's own question; only a blank
     * question that starts a conversation (or a stateless request) opens one.
     * Its context is the subject's canonical one, so nothing else a caller
     * sent (figures included) reaches the prompt. Refused with a 400: a
     * malformed subject ([PageQuerySelector.INVALID_HOLDING_ANALYSIS]), one
     * asked inside an Asset Review conversation — about the asset alone —
     * ([PageQuerySelector.HOLDING_IN_ASSET_REVIEW]), and a blank question that
     * would open nothing. Any other page is planned as [withQuestion] and
     * [canonical] make it.
     */
    private fun planFor(body: AgentQuery): Plan {
        val thread = threadOf(body)
        val subject = pageQuerySelector.holdingAnalysisSubject(body.context)
        if (subject != null && thread?.kind == ConversationKind.ASSET_REVIEW) {
            throw BusinessException(PageQuerySelector.HOLDING_IN_ASSET_REVIEW)
        }
        val followUp = thread?.kind == ConversationKind.HOLDING_ANALYSIS && thread.hasTurns
        if (subject == null && !followUp) return Plan(withQuestion(canonical(body)), null, thread)
        val context =
            subject?.let(pageQuerySelector::holdingAnalysisContext)
                ?: mapOf("page" to PageQueries.HOLDING_ANALYSIS)
        val opens = body.query.isBlank() && body.history.isNullOrEmpty() && thread?.hasTurns != true
        if (body.query.isBlank() && !opens) throw BusinessException(QUERY_REQUIRED)
        return Plan(body.copy(context = context), if (opens) subject else null, thread)
    }

    /**
     * The opening turn of a Holding Analysis. The caller's holding is read
     * first, server side with their own credentials, so a holding they do not
     * have costs no model call; then the asset's review, cached or generated
     * ([assetReview]). The question carries both: the review labelled with its
     * date, then the holding's figures, then [PageQueries.holdingAnalysis]'s
     * ask. Never cached: it is the caller's own.
     */
    private fun holdingOpening(
        request: AgentQuery,
        subject: HoldingAnalysisSubject
    ): Opening =
        try {
            val facts = holdings.find(subject)
            if (facts == null) {
                Opening.Failed(NOT_HELD)
            } else {
                val reviewDate = LocalDate.now(clock)
                val review = assetReview(subject.asset, reviewDate, request)
                Opening.Ready(
                    request.copy(
                        query = PageQueries.holdingAnalysis(subject, reviewDate, review, facts),
                        label = request.label ?: PageQueries.holdingAnalysisLabel(subject.ticker)
                    )
                )
            }
        } catch (
            // Before any stream exists: classified like a stream failure, never a 500.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            val code = classifyError(e)
            log.error("Holding analysis could not open ({}): {}", code, e.message, e)
            Opening.Failed(code)
        }

    /**
     * The Asset Review of [asset] on [reviewDate]: the cached one when fresh,
     * else asked of the model exactly as the canonical cached review is —
     * from the asset alone, never the caller's context — and stored for every
     * caller, as [ReviewCachingRecorder] would.
     */
    private fun assetReview(
        asset: AssetReviewSubject,
        reviewDate: LocalDate,
        request: AgentQuery
    ): String {
        val turn =
            withQuestion(
                AgentQuery(
                    context = pageQuerySelector.assetReviewContext(asset),
                    think = request.think,
                    deepThink = request.deepThink
                )
            )
        val key = AssetReviewKey(asset.market, asset.ticker, reviewDate, answeringModel(turn))
        val hit = cachedReview(key, request.refresh)
        if (hit != null) return hit
        val answer = ask(turn)
        if (answer.content.isBlank()) throw UnansweredTurn(emptyAnswerCode(answer.finishReason))
        try {
            reviewCache.store(key, answer.content)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to cache asset review {}:{}: {}", key.market, key.ticker, e.message)
        }
        return answer.content
    }

    /** One blocking model answer and why it ended. */
    private data class ModelAnswer(
        val content: String,
        val finishReason: String?,
        val modelId: String
    )

    /**
     * Ask the model [turn] and wait for the answer. Per-call options REPLACE
     * (not merge) the ChatClient's default options — Anthropic cache config
     * must be re-applied here, or every request silently loses prompt
     * caching. See [buildOptions].
     */
    private fun ask(turn: AgentQuery): ModelAnswer {
        val userMessage = buildUserMessage(turn)
        val tools = toolSelector.selectTools(turn.context)
        val modelId = chatModelSelector.selectFor(turn.context, turn.deepThink)
        val startMs = System.currentTimeMillis()
        val promptSpec =
            clientFor(turn)
                .prompt()
                .system(systemPromptFor(turn))
                .messages(historyMessages(turn))
                .user(userMessage)
                .tools(*tools)
        val callResponse =
            buildOptions(modelId, turn.deepThink, thinks(turn))?.let { opts ->
                promptSpec.options(opts).call()
            } ?: promptSpec.call()
        val chatResponse = callResponse.chatResponse()
        val content = chatResponse?.result?.output?.text ?: callResponse.content() ?: ""
        logLlmInteraction(userMessage, tools, chatResponse, content, System.currentTimeMillis() - startMs, modelId)
        return ModelAnswer(content, chatResponse?.result?.metadata?.finishReason, modelId)
    }

    /** A failed `/query`, in the shape every failure takes. */
    private fun failure(
        safeQuery: String,
        code: String
    ): ResponseEntity<AgentResponse> =
        ResponseEntity
            .status(statusFor(code))
            .body(
                AgentResponse(
                    query = safeQuery,
                    response = messageFor(code),
                    timestamp = Instant.now().toString(),
                    error = code
                )
            )

    /**
     * A canned Asset Review that may be shared, rebuilt from its cache key
     * alone: the page, ticker and market, with the server's label. A shared
     * answer reaches every user, so caller-supplied context (`assetName`, any
     * other key) and label must never shape its prompt. Anything else is
     * returned as sent.
     */
    private fun canonical(body: AgentQuery): AgentQuery {
        if (!reviewCache.enabled || body.query.isNotBlank() || !body.history.isNullOrEmpty()) return body
        val subject = pageQuerySelector.assetReviewSubject(body.context) ?: return body
        return body.copy(context = pageQuerySelector.assetReviewContext(subject), label = null)
    }

    /**
     * Where this turn's canned Asset Review is cached, or null when it is not
     * one: the caller asked nothing of its own and neither the request nor the
     * conversation carries earlier turns. Read before the turn is recorded, so
     * a cache hit is known before the quota is asked. A free-form question or a follow-up
     * is always answered live. The review reads no user data (see
     * [ToolSelector]), so it is keyed by the asset alone, the date stamped
     * into the prompt, and the model that answers — never by the caller.
     */
    private fun reviewKeyFor(
        body: AgentQuery,
        turn: AgentQuery,
        thread: ConversationThread?
    ): AssetReviewKey? {
        if (!reviewCache.enabled) return null
        if (body.query.isNotBlank() || !body.history.isNullOrEmpty() || thread?.hasTurns == true) return null
        val subject = pageQuerySelector.assetReviewSubject(turn.context) ?: return null
        return AssetReviewKey(
            market = subject.market,
            ticker = subject.ticker,
            reviewDate = LocalDate.now(clock),
            model = answeringModel(turn)
        )
    }

    /**
     * The model that actually answers [turn]: the selected tier where the
     * per-call override applies ([buildOptions]: Anthropic and DeepSeek, where
     * `deepThink` selects the deep tier's model), else the provider's own
     * configured model, named by the provider since every tier gets it.
     */
    private fun answeringModel(turn: AgentQuery): String =
        if (anthropicActive || deepseekActive) chatModelSelector.selectFor(turn.context, turn.deepThink) else provider

    /**
     * The cached answer for [key], or null on a miss or when the caller asked
     * to [AgentQuery.refresh] it. A failed read is a miss.
     */
    private fun cachedReview(
        key: AssetReviewKey,
        refresh: Boolean
    ): String? {
        val hit =
            if (refresh) {
                null
            } else {
                try {
                    reviewCache.find(key)
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception
                ) {
                    log.warn("Asset review cache read failed: {}", e.message)
                    null
                }
            }
        log.debug(
            "Asset review cache {} for {}:{} on {}",
            if (refresh) {
                "refresh"
            } else if (hit == null) {
                "miss"
            } else {
                "hit"
            },
            key.market,
            key.ticker,
            key.reviewDate
        )
        return hit
    }

    /**
     * Record a blocking-path turn. Like [record], a failure to save is logged,
     * never allowed to cost the user the answer.
     */
    private fun recordQuietly(action: () -> Unit) {
        try {
            action()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Failed to record conversation turn: {}", e.message, e)
        }
    }

    /**
     * Record off the Reactor event loop (JPA blocks). A failure to save is
     * logged, never allowed to cost the user the answer they are reading.
     */
    private fun record(action: () -> Unit): Mono<Void> =
        Mono
            .fromRunnable<Void>(action)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError { e -> log.error("Failed to record conversation turn: {}", e.message, e) }
            .onErrorComplete()

    /**
     * Map caller-supplied conversation history onto Spring AI [Message]s,
     * truncated to the trailing [MAX_HISTORY_TURNS]. Plain text only — this
     * replays the model's own prior *rendered* answers as ordinary assistant
     * turns, never the raw `reasoning_content` a thinking-mode response
     * carries, so it doesn't touch the known DeepSeek thinking-mode
     * multi-turn tool-call round-trip issue.
     */
    internal fun historyMessages(request: AgentQuery): List<Message> =
        request.history
            ?.takeLast(MAX_HISTORY_TURNS)
            ?.map { turn ->
                if (turn.role == "assistant") AssistantMessage(turn.content) else UserMessage(turn.content)
            }
            ?: emptyList()

    /** Selected provider: the `agent.provider` property, `deepseek` when absent. */
    private val provider: String
        get() = environment.getProperty(AgentProvider.PROPERTY, AgentProvider.DEFAULT)

    /**
     * Anthropic-only feature: the per-call model override uses
     * [AnthropicChatOptions]. For any other provider, skip the override and
     * let the configured ChatClient's default model run.
     */
    private val anthropicActive: Boolean
        get() = provider == AgentProvider.ANTHROPIC

    private val deepseekActive: Boolean
        get() = provider == AgentProvider.DEEPSEEK

    /**
     * Build per-call ChatOptions for the active LLM surface.
     *
     * Returns `null` for surfaces that don't support a per-call model override
     * (Ollama / OpenAI), in which case the ChatClient's configured default
     * model answers and tier escalation is silently ignored.
     *
     * `deepThink` raises `maxTokens` so the deep tier (deepseek-v4-pro /
     * claude-opus-*) has headroom for chain-of-thought + final answer; on the
     * Anthropic surface it also explicitly enables thinking with a 4k budget
     * (Claude 4 has thinking on by default; setting it explicitly documents
     * intent and lets the budget be tuned).
     *
     * `think` (the interactive Chat FAB) routes to DeepSeek's thinking client,
     * where `max_tokens` caps reasoning_content *and* the answer together —
     * DeepSeek's own default in thinking mode is 64k. Under the fast-tier 4k
     * cap a v4-flash turn on kauri spent the whole budget reasoning about a
     * 27k-token prompt and finished on `length` with no answer at all, so a
     * thinking turn gets the same headroom as the deep tier.
     *
     * The thinking client defaults every request to `reasoning_effort: low`
     * (beancounter#1128); a `think` + `deepThink` turn pins `high` here so the
     * deep tier keeps full effort. Effort is never set without `think` — any
     * value re-enables thinking on the fast client. Callers pass [thinks], so
     * `think` is also true for a SMART page's pre-canned prompt: such a turn
     * reasons at low effort, or at high effort on the deep tier.
     */
    internal fun buildOptions(
        modelId: String,
        deepThink: Boolean,
        think: Boolean = false
    ): org.springframework.ai.chat.prompt.ChatOptions.Builder<*>? =
        when {
            anthropicActive -> {
                val b = AnthropicChatOptions.builder().model(modelId)
                anthropicCacheOptions?.let(b::cacheOptions)
                if (deepThink) {
                    b
                        .maxTokens(THINKING_MAX_TOKENS)
                        // Spring AI 2.0: the two-arg thinking(type, budget) is
                        // gone; thinkingEnabled(budgetTokens) is the explicit
                        // enable + budget call (4k budget preserved).
                        .thinkingEnabled(4096L)
                }
                b
            }
            deepseekActive -> {
                val b =
                    org.springframework.ai.deepseek.DeepSeekChatOptions
                        .builder()
                        .model(modelId)
                        .maxTokens(if (deepThink || think) THINKING_MAX_TOKENS else FAST_MAX_TOKENS)
                if (deepThink && think) b.reasoningEffortHigh()
                b
            }
            else -> {
                null
            }
        }

    @GetMapping("/health")
    @Operation(
        summary = "Traffic-light health check for the agent and its downstream services.",
        description =
            "Pings actuator/health on bc-data, bc-position and bc-event, and reports " +
                "whether a Spring AI ChatClient is configured. Unauthenticated."
    )
    fun health(): AgentHealthResponse = healthChecker.check(llmAvailable = chatClient != null)

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
        val plan = planFor(body)
        val request = plan.request
        if (chatClient == null) {
            return ResponseEntity
                .status(503)
                .body(
                    AgentResponse(
                        query = HtmlUtils.htmlEscape(request.query),
                        response =
                            "No LLM is configured. Set ${AgentProvider.PROPERTY} to '${AgentProvider.DEEPSEEK}', " +
                                "'${AgentProvider.ANTHROPIC}', '${AgentProvider.OPENAI}', or '${AgentProvider.OLLAMA}'.",
                        timestamp = Instant.now().toString(),
                        error = NO_LLM
                    )
                )
        }

        val reviewKey = if (plan.opening == null) reviewKeyFor(body, request, plan.thread) else null
        val hit = reviewKey?.let { cachedReview(it, body.refresh) }
        val meter = meterFor(body, plan)
        val refused = if (hit == null) refusalFor(meter) else null
        if (refused != null) {
            return failure(HtmlUtils.htmlEscape(openingLabel(request, plan), UTF_8), refused)
        }
        val asked =
            when (val opening = plan.opening?.let { holdingOpening(request, it) } ?: Opening.Ready(request)) {
                // Echo what the caller asked about: the opening question was never built.
                is Opening.Failed -> return failure(
                    HtmlUtils.htmlEscape(openingLabel(request, plan), UTF_8),
                    opening.code
                )
                is Opening.Ready -> opening.turn
            }
        val safeQuery = HtmlUtils.htmlEscape(asked.query)
        val (turn, conversationRecorder) = openTurn(asked, plan.thread)
        if (hit != null) {
            // No model runs: the stored answer is recorded as a live one would be, and not counted.
            llmMetrics.cached(LlmMetrics.Mode.CALL, reviewKey.model)
            recordQuietly { conversationRecorder.answered(hit) }
            return ResponseEntity.ok(
                AgentResponse(
                    query = safeQuery,
                    response = hit,
                    timestamp = Instant.now().toString(),
                    cached = true
                )
            )
        }
        val recorder =
            metered(
                reviewKey?.let { ReviewCachingRecorder(conversationRecorder, reviewCache, it) } ?: conversationRecorder,
                meter
            )
        return try {
            val answer = ask(turn)
            val content = answer.content

            // Same hole the streaming path had: a turn can succeed at the HTTP
            // level and still say nothing (typically after the context window
            // filled with tool results). Answering with a blank body left every
            // caller rendering an empty response and no reason for it.
            if (content.isBlank()) {
                val code = emptyAnswerCode(answer.finishReason)
                log.warn(
                    "Agent query produced no answer: code={}, model={}, query=\"{}\"",
                    code,
                    answer.modelId,
                    safeQuery.take(QUERY_LOG_CHARS)
                )
                recordQuietly { recorder.failed(code) }
                return failure(safeQuery, code)
            }

            recordQuietly { recorder.answered(content) }
            ResponseEntity.ok(
                AgentResponse(
                    query = safeQuery,
                    response = content,
                    timestamp = Instant.now().toString()
                )
            )
        } catch (
            // Controller boundary: every LLM/tool failure becomes an error
            // body, classified the same way the streaming path classifies it.
            // Without this the two transports disagreed — a stream told the UI
            // "provider-quota", the same failure on /query told it
            // "agent-error", and the non-streaming surfaces (Asset Review, News
            // Sentiment) could only ever say something went wrong.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            val errorCode = classifyError(e)
            log.error("Agent query failed ({}): {}", errorCode, e.message, e)
            recordQuietly { recorder.failed(errorCode) }
            failure(safeQuery, errorCode)
        }
    }

    /**
     * Streaming variant of [query]. Returns a Server-Sent Events stream so
     * the browser sees a first byte within ~1–2s instead of waiting for the
     * full LLM + tool-call chain to complete (which can run 30–60s on the
     * heavier Independence / Rebalance domains and trip mobile-Safari's
     * idle-timeout, surfacing as "Load failed".
     *
     * Event protocol:
     *   - `event: token` `data: <text-chunk>` — one per emitted text fragment
     *   - `event: done`  `data: {chars, elapsed_ms[, model]}` — final summary;
     *                    `model` is only present when the per-call Anthropic
     *                    override was applied (i.e. the active profile is
     *                    Anthropic), since on `ollama`/`openai` the underlying
     *                    ChatClient picks the model and we don't surface it.
     *   - `event: error` `data: <opaque-code>` — terminal; payload is a stable
     *                    code (e.g. `"agent-error"`), never the raw exception.
     *
     * A turn in a conversation outlives its subscriber. The question is
     * already in history by the time the stream starts ([openTurn]), and the
     * answer is written only when the pipeline ends — so a client that drops
     * mid-answer (a phone that sleeps during a 40s Independence turn) would
     * otherwise leave an orphan question: the MVC emitter cancels a cold Flux,
     * the LLM call aborts, and cancellation reaches neither `onErrorResume`
     * nor the end-of-stream recording. So for a conversation the pipeline is
     * made hot and connected on the request thread, which the subscriber's
     * cancel cannot disconnect; [InFlightTurns] marks the conversation as
     * being answered — set before the question is written, so no reader ever
     * sees it unanswered and unclaimed — until the pipeline itself ends. A
     * turn refused by [openTurn] clears the mark again. A stateless request has
     * no history to complete and stays cold: dropping it stops the LLM call.
     */
    @PostMapping("/query/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "Streaming variant of /agent/query (Server-Sent Events).",
        description =
            "Same inputs as /agent/query but emits the LLM response token-by-token " +
                "as `text/event-stream`. Use this from clients that risk hitting " +
                "browser idle-timeouts on long queries."
    )
    fun stream(
        @RequestBody body: AgentQuery
    ): Flux<ServerSentEvent<String>> {
        scopeAuthorizer.authorize()
        val plan = planFor(body)
        // A code, not prose: the streaming path's error payload is a contract
        // the UI renders copy from, and `/query` already answers this case
        // with the same NO_LLM code.
        if (chatClient == null) return errorEvent(NO_LLM)
        val reviewKey = if (plan.opening == null) reviewKeyFor(body, plan.request, plan.thread) else null
        val hit = reviewKey?.let { cachedReview(it, body.refresh) }
        val meter = meterFor(body, plan)
        // Refused before the question is recorded: an exhausted caller leaves no residue.
        val refused = if (hit == null) refusalFor(meter) else null
        if (refused != null) return errorEvent(refused)
        val conversationId = plan.thread?.id
        // Pending before the question is committed: openTurn writes the user
        // row in its own transaction, and a GET landing between that commit
        // and this mark would show an unanswered question nobody is answering.
        // A refused turn (not owned, owner lookup failed) leaves no residue.
        conversationId?.let(inFlightTurns::begin)
        val pipeline =
            try {
                val subject = plan.opening
                if (subject == null) {
                    val (turn, recorder) = openTurn(plan.request, plan.thread)
                    pipelineFor(reviewKey, hit, turn, recorder, meter)
                } else {
                    openingPipeline(plan.request, subject, plan.thread, meter)
                }
            } catch (e: Exception) {
                conversationId?.let(inFlightTurns::end)
                throw e
            }
        if (conversationId == null) return pipeline
        // doFinally sits inside the hot boundary, so it fires when the
        // pipeline itself completes or fails — after the record{} write that
        // ends it — and never on the subscriber's cancel. replay() keeps the
        // tokens emitted before the MVC emitter subscribes (it attaches after
        // this method returns); autoConnect(0) subscribes right here, on the
        // request thread, so contextCapture still snapshots the caller's
        // SecurityContext, and unlike refCount it never disconnects when the
        // downstream count drops to zero.
        return pipeline
            .doFinally { inFlightTurns.end(conversationId) }
            .replay()
            .autoConnect(0)
    }

    /**
     * The opening of a Holding Analysis as a stream. Reading the holding and
     * asking the review block, so that work runs on a worker thread once the
     * stream is subscribed, never while the request thread is setting it up.
     * The publishOn hop delivers the subject with the caller's SecurityContext
     * restored from the context captured here, so svc-position and the
     * review's tools are called as the caller. A failure before the question
     * is recorded ends the stream with its code and records nothing.
     */
    private fun openingPipeline(
        request: AgentQuery,
        subject: HoldingAnalysisSubject,
        thread: ConversationThread?,
        meter: String?
    ): Flux<ServerSentEvent<String>> =
        Mono
            .just(subject)
            .publishOn(Schedulers.boundedElastic())
            .flatMapMany { holding ->
                when (val opening = holdingOpening(request, holding)) {
                    is Opening.Failed -> {
                        errorEvent(opening.code)
                    }
                    is Opening.Ready -> {
                        val (turn, recorder) = openTurn(opening.turn, thread)
                        answerPipeline(turn, metered(recorder, meter))
                    }
                }
            }.onErrorResume { e ->
                val code = classifyError(e)
                log.error("Holding analysis stream failed ({}): {}", code, e.message, e)
                errorEvent(code)
            }.contextCapture()

    /** What a Holding Analysis opening is stored under: the caller's label, else the subject's. */
    private fun openingLabel(
        request: AgentQuery,
        plan: Plan
    ): String = request.label ?: plan.opening?.let { PageQueries.holdingAnalysisLabel(it.ticker) } ?: request.query

    /**
     * A canned Asset Review answered from the cache [hit] when one is fresh,
     * uncounted; else live, keeping a successful answer under [key] for next
     * time and counting it against [meter].
     */
    private fun pipelineFor(
        key: AssetReviewKey?,
        hit: String?,
        turn: AgentQuery,
        recorder: TurnRecorder,
        meter: String?
    ): Flux<ServerSentEvent<String>> {
        if (key != null && hit != null) return cachedPipeline(hit, key, recorder)
        val live = key?.let { ReviewCachingRecorder(recorder, reviewCache, it) } ?: recorder
        return answerPipeline(turn, metered(live, meter))
    }

    /**
     * Who [body]'s turn is counted against: the caller's `SystemUser.id` when
     * metered ([AgentScopeAuthorizer.metered]), else null. Read on the request
     * thread, where the owner lookup has the caller's token.
     */
    private fun meterFor(
        body: AgentQuery,
        plan: Plan
    ): String? = if (scopeAuthorizer.metered(body)) plan.thread?.ownerId ?: conversationOwner.id() else null

    /**
     * Why a live turn is refused, or null to run it: an unconfirmed email
     * address first, then a metered caller an administrator has disabled, then
     * one with no turn left this week. A free one-off News lookup is never
     * metered, so it needs no caller identity and is not refused for either.
     * Asked only on an Asset Review cache miss: a hit is served free to anyone.
     */
    private fun refusalFor(meter: String?): String? =
        when {
            !scopeAuthorizer.verified() -> EMAIL_UNVERIFIED
            meter == null -> null
            quota.disabled(meter) -> AI_DISABLED
            !quota.allows(meter) -> AI_QUOTA
            else -> null
        }

    /** [recorder], counting an answered turn against [meter] when metered. */
    private fun metered(
        recorder: TurnRecorder,
        meter: String?
    ): TurnRecorder = meter?.let { QuotaRecorder(recorder, quota, it) } ?: recorder

    /**
     * Stream a cached review the way a live answer ends: its text as a token,
     * the turn recorded, then `done` flagged `cached`. No model is called.
     * `model` follows the live rule: present only on the Anthropic surface.
     */
    private fun cachedPipeline(
        content: String,
        key: AssetReviewKey,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val startMs = System.currentTimeMillis()
        val requestSpan =
            io.opentelemetry.api.trace.Span
                .current()
        val done =
            Flux.defer {
                llmMetrics.cached(LlmMetrics.Mode.STREAM, key.model, requestSpan)
                val payload =
                    objectMapper.writeValueAsString(
                        buildMap {
                            put("chars", content.length.toLong())
                            put("elapsed_ms", System.currentTimeMillis() - startMs)
                            if (anthropicActive) put("model", key.model)
                            put("cached", true)
                        }
                    )
                Flux.just(ServerSentEvent.builder(payload).event("done").build())
            }
        return Flux
            .just(ServerSentEvent.builder(content).event("token").build())
            .concatWith(record { recorder.answered(content) }.thenMany(Flux.empty()))
            .concatWith(done)
    }

    /**
     * The cold generation pipeline for one turn. Wrapped in Flux.defer so
     * setup-time exceptions (selector failures, options builder failures)
     * become Flux errors and reach onErrorResume rather than escaping out of
     * the controller as a 500.
     *
     * .contextCapture() snapshots the subscribing thread's ThreadLocals
     * (incl. SecurityContext via SecurityContextPropagationConfig) so tool
     * callbacks invoked on Reactor scheduler threads still see the caller's
     * JWT — without it, TokenService.jwt would throw "Not authorised" on
     * every tool call.
     */
    private fun answerPipeline(
        turn: AgentQuery,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> =
        Flux
            .defer { runStream(turn, recorder) }
            .onErrorResume { e ->
                // Never return e.message — leaks internals. Log full detail
                // server-side; client gets a stable, classified code so the
                // UI can surface a real cause instead of generic
                // "agent-error" when the failure is something the user can
                // act on (e.g. provider quota, rate limit).
                log.error("Agent stream failed: {}", e.message, e)
                val code = classifyError(e)
                record { recorder.failed(code) }.thenMany(errorEvent(code))
            }.contextCapture()

    /**
     * Map an upstream exception into a stable, opaque error code, used by both
     * transports. The client renders a friendly message keyed off the code;
     * the raw exception text never reaches the client.
     *
     *   `provider-quota`   — The LLM account has no credit left. Anthropic
     *                        says so with HTTP 400 + "credit balance";
     *                        DeepSeek with HTTP 402 + "Insufficient Balance".
     *                        Nothing the caller does can clear it — an admin
     *                        has to top the account up.
     *   `provider-rate`    — Provider rate-limited the request (HTTP 429).
     *   `provider-timeout` — Upstream took too long.
     *   `agent-error`      — Anything else.
     *
     * Both the exception and its causes are inspected: Reactor and Spring AI
     * re-wrap the provider error on the way out, so the status is often one or
     * two levels down.
     */
    internal fun classifyError(e: Throwable): String {
        if (e is UnansweredTurn) return e.code
        val chain = causeChain(e)
        val message = chain.joinToString(" | ") { it.message.orEmpty() }
        val statuses = chain.mapNotNull(::httpStatusOf)

        // Distinctive phrases are matched across the whole chain — no other
        // failure says "credit balance" or "insufficient balance".
        val isCreditBalance = message.contains("credit balance", ignoreCase = true)
        val isOutOfCredit =
            message.contains("insufficient balance", ignoreCase = true) ||
                message.contains("insufficient_quota", ignoreCase = true)
        // "billing" is not distinctive enough for that, and neither is a bare
        // "400". Anthropic's billing rejection is only trusted when one
        // exception carries both — otherwise a "billing" in a tool result and
        // a 400 from an unrelated wrapper would combine into a false quota.
        val isBilling400 =
            chain.any { cause ->
                httpStatusOf(cause) == BAD_REQUEST &&
                    cause.message.orEmpty().contains("billing", ignoreCase = true)
            }
        if (statuses.contains(PAYMENT_REQUIRED) || isCreditBalance || isBilling400 || isOutOfCredit) {
            return PROVIDER_QUOTA
        }
        // Likewise a bare "429": the status is read from where it is trusted,
        // and the wording covers providers that report it in prose.
        if (statuses.contains(TOO_MANY_REQUESTS) ||
            message.contains("too many requests", ignoreCase = true) ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("rate_limit", ignoreCase = true)
        ) {
            return PROVIDER_RATE
        }
        if (chain.any { it is java.util.concurrent.TimeoutException } ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
        ) {
            return PROVIDER_TIMEOUT
        }
        return AGENT_ERROR
    }

    /** The exception plus its causes, depth-capped so a cyclic chain can't hang us. */
    private fun causeChain(e: Throwable): List<Throwable> =
        generateSequence(e) { current -> current.cause?.takeIf { it !== current } }
            .take(CAUSE_CHAIN_DEPTH)
            .toList()

    /**
     * The provider's HTTP status, where it is actually knowable. WebClient
     * (streaming path) carries it as a typed field; Spring AI's blocking error
     * handler only formats it as `"<status> - <body>"`, so it is read back from
     * the head of the message. Anything else yields null rather than a guess —
     * a status scraped from anywhere in the text would match numbers that are
     * part of a tool result.
     */
    private fun httpStatusOf(e: Throwable): Int? =
        when (e) {
            is org.springframework.web.reactive.function.client.WebClientResponseException -> {
                e.statusCode.value()
            }
            else -> {
                LEADING_HTTP_STATUS
                    .find(e.message.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        }

    /**
     * HTTP status for a failed blocking `/query`, so the browser can tell an
     * administrative outage from a broken request without parsing prose.
     * 402 is passed straight through: it means the same thing to us as it does
     * to the provider — the account behind the feature needs topping up.
     */
    private fun statusFor(errorCode: String): Int =
        when (errorCode) {
            PROVIDER_QUOTA -> PAYMENT_REQUIRED
            PROVIDER_RATE, AI_QUOTA -> TOO_MANY_REQUESTS
            PROVIDER_TIMEOUT -> GATEWAY_TIMEOUT
            // Not a server fault: the request was well-formed, the provider
            // call succeeded, and the model finished — it just produced no
            // answer, usually because tool results filled its context window.
            // 500 would read as "bc-agent broke" and page on a condition only
            // a narrower question can fix. The streaming path reports the same
            // codes over a 200 SSE stream; the code, not the status, is the
            // contract both transports share.
            ANSWER_TRUNCATED, EMPTY_ANSWER -> UNPROCESSABLE_CONTENT
            NOT_HELD -> NOT_FOUND
            EMAIL_UNVERIFIED, AI_DISABLED -> FORBIDDEN
            else -> INTERNAL_ERROR
        }

    /**
     * Fallback prose for clients that render the body instead of keying off
     * [AgentResponse.error]. Deliberately says whose problem it is; the rich
     * copy lives in the UI, next to the retry affordances.
     */
    private fun messageFor(errorCode: String): String =
        when (errorCode) {
            PROVIDER_QUOTA -> {
                "AI features are paused: the AI provider account has run out of credit. " +
                    "This is a service administration issue, not a problem with your request. " +
                    "Please try again once the balance has been topped up."
            }
            PROVIDER_RATE -> {
                "The AI provider is rate-limiting requests. Please wait a moment and try again."
            }
            PROVIDER_TIMEOUT -> {
                "The AI provider took too long to respond. Please try again."
            }
            ANSWER_TRUNCATED -> {
                "The question needed more data than the AI can hold at once, so it ran out of room " +
                    "before answering. Try asking about one plan, phase or holding at a time."
            }
            EMPTY_ANSWER -> {
                "The AI finished without producing an answer. Please try again, or rephrase the question."
            }
            NOT_HELD -> {
                "No holding of this asset found in that portfolio."
            }
            AI_QUOTA -> {
                "You have used this week's AI questions. Each one frees up seven days after it was asked."
            }
            AI_DISABLED -> {
                "AI is turned off for your account. Ask the administrator to turn it back on."
            }
            EMAIL_UNVERIFIED -> {
                "Confirm your email address to ask the AI. Cached Asset Reviews are still available."
            }
            else -> {
                "The agent failed to process the request."
            }
        }

    private fun runStream(
        request: AgentQuery,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val safeQuery = HtmlUtils.htmlEscape(request.query)
        val tools = toolSelector.selectTools(request.context)
        val modelId = chatModelSelector.selectFor(request.context, request.deepThink)
        val startMs = System.currentTimeMillis()
        // Pin the OTel span at request-time so the doneEvent lambda — which
        // runs on a Reactor boundedElastic thread — writes telemetry to the
        // request's http.server span rather than a noop fallback.
        val requestSpan =
            io.opentelemetry.api.trace.Span
                .current()

        val totalChars = AtomicLong(0)
        // The *answer* — narration before a tool turn doesn't count, since the
        // reader is left with nothing when the turn that follows it never
        // produces text. Cleared at every tool-turn boundary, which is why this
        // can't stand in for `totalChars` (that one guards connect-retry and
        // has to count every char ever streamed). It is also what a
        // conversation records as the assistant's reply.
        val answer = StringBuilder()
        // Last non-blank finish reason seen, for classifying an answerless
        // stream: `length`/`max_tokens` mean truncation, anything else means
        // the model simply said nothing.
        val lastFinishReason = AtomicReference<String?>(null)
        // Spring AI's chatResponse() Flux surfaces ChatResponse per chunk; the
        // final emission carries usage tokens. Capture it so doneEvent can
        // ship token measurements to Sentry alongside char count + elapsed.
        val capturedUsage = AtomicReference<Usage?>(null)

        val streamSpec = buildStreamSpec(request, tools, modelId)
        val tokenEvents =
            streamSpec
                .chatResponse()
                .doOnNext { resp -> capturedUsage.set(resp.metadata.usage) }
                // Spring AI emits trailing ChatResponse chunks with no Generation
                // (metadata-only — token usage, finishReason). resp.result is
                // null-safe throughout: sseEventsFor tolerates empty text.
                .concatMap { resp ->
                    val text =
                        resp.result
                            ?.output
                            ?.text
                            .orEmpty()
                    val finishReason = resp.result?.metadata?.finishReason
                    if (text.isNotEmpty()) {
                        totalChars.addAndGet(text.length.toLong())
                        answer.append(text)
                    }
                    val reason = finishReason?.takeIf { it.isNotBlank() }?.lowercase()
                    if (reason != null) {
                        lastFinishReason.set(reason)
                        // Whatever was said before the tool call was narration,
                        // not the answer — the answer is what comes after.
                        if (reason in TOOL_TURN_FINISH_REASONS) answer.setLength(0)
                    }
                    Flux.fromIterable(sseEventsFor(text, finishReason))
                }
        val doneEvent =
            Flux.defer {
                doneEvent(
                    modelId = modelId,
                    tools = tools,
                    totalChars = totalChars.get(),
                    usage = capturedUsage.get(),
                    startMs = startMs,
                    safeQuery = safeQuery,
                    requestSpan = requestSpan
                )
            }
        // Transient connectivity blips (e.g. a sub-second DNS failure resolving
        // api.deepseek.com) surface as WebClientRequestException at connect time
        // — before any token is emitted. Retry those rather than failing the
        // user's request. The `totalChars == 0` guard makes retries safe: once
        // content has streamed to the client, re-running would duplicate it, so
        // a later drop is allowed to propagate to the error envelope.
        val retriedTokens =
            tokenEvents.retryWhen(
                Retry
                    .backoff(STREAM_RETRY_ATTEMPTS, STREAM_RETRY_MIN_BACKOFF)
                    .filter { e -> totalChars.get() == 0L && isTransientConnectivity(e) }
                    // Surface the original cause, not Reactor's RetryExhausted
                    // wrapper, so classifyError still maps it accurately.
                    .onRetryExhaustedThrow { _, signal -> signal.failure() }
            )
        // A stream that ends without an answer is a failure the client can't
        // otherwise see: HTTP was 200, no exception was thrown, and the `done`
        // envelope carries metadata only — so the UI would render an empty
        // assistant bubble and say nothing about why. Classified and emitted
        // as a normal error event, using the same codes both transports share.
        val emptyAnswerEvent =
            Flux.defer {
                if (answer.isNotEmpty()) {
                    val content = answer.toString()
                    record { recorder.answered(content) }.thenMany(Flux.empty())
                } else {
                    val code = emptyAnswerCode(lastFinishReason.get())
                    log.warn(
                        "Agent stream produced no answer: code={}, finish_reason={}, model={}, query=\"{}\"",
                        code,
                        lastFinishReason.get(),
                        modelId,
                        safeQuery.take(QUERY_LOG_CHARS)
                    )
                    record { recorder.failed(code) }.thenMany(errorEvent(code))
                }
            }
        return retriedTokens.concatWith(emptyAnswerEvent).concatWith(doneEvent)
    }

    /**
     * Why a turn ended with no answer text. `length`/`max_tokens` mean the
     * model ran out of room — on the heavy domains that is the context window
     * being consumed by tool results, which is actionable (ask something
     * narrower) in a way that a bare "something went wrong" is not.
     */
    private fun emptyAnswerCode(finishReason: String?): String =
        if (finishReason?.lowercase() in TRUNCATION_FINISH_REASONS) ANSWER_TRUNCATED else EMPTY_ANSWER

    /**
     * True when the failure is a transient network/connectivity error worth
     * retrying — a connection or DNS-resolution failure anywhere in the cause
     * chain. Deterministic provider errors (4xx, rate limits) are excluded so
     * we don't retry the inevitable.
     */
    internal fun isTransientConnectivity(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is ConnectException || cause is UnresolvedAddressException) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Pick the LLM client for this request. Pre-canned prompts (the default,
     * `think=false`) use the non-thinking [fastChatClient] for lowest latency,
     * except on the SMART pages, which reason at low effort; the interactive
     * Chat FAB sets `think=true` to keep DeepSeek thinking mode.
     * Falls back to the thinking client if no fast client is configured.
     * Callers guard `chatClient != null` first, so the result is non-null.
     */
    private fun clientFor(request: AgentQuery): ChatClient =
        (if (thinks(request)) chatClient else fastChatClient ?: chatClient)!!

    /**
     * Whether this turn thinks: asked for (the Chat FAB), or a SMART page's pre-canned prompt,
     * which reasons at the thinking client's low effort ([ChatModelSelector.reasonsFor]).
     */
    private fun thinks(request: AgentQuery): Boolean = request.think || chatModelSelector.reasonsFor(request.context)

    private fun buildStreamSpec(
        request: AgentQuery,
        tools: Array<Any>,
        modelId: String
    ): ChatClient.StreamResponseSpec {
        val promptSpec =
            clientFor(request)
                .prompt()
                .system(systemPromptFor(request))
                .messages(historyMessages(request))
                .user(buildUserMessage(request))
                .tools(*tools)
        return buildOptions(modelId, request.deepThink, thinks(request))?.let { opts ->
            promptSpec.options(opts).stream()
        } ?: promptSpec.stream()
    }

    private fun doneEvent(
        modelId: String,
        tools: Array<Any>,
        totalChars: Long,
        usage: Usage?,
        startMs: Long,
        safeQuery: String,
        requestSpan: io.opentelemetry.api.trace.Span
    ): Flux<ServerSentEvent<String>> {
        val elapsedMs = System.currentTimeMillis() - startMs
        // Token telemetry as OTel attributes on the captured request span
        // (same span the http.server transaction owns). Same shape as the
        // non-streaming path so dashboards can mix call+stream traffic.
        // Only attribute the model tag when the per-call Anthropic override
        // was applied — on ollama / openai the configured ChatClient picks
        // the model and our selectedModelId would mislead the metric.
        llmMetrics.capture(
            modelId = modelId.takeIf { anthropicActive },
            usage = usage,
            elapsedMs = elapsedMs,
            toolCount = tools.size,
            mode = LlmMetrics.Mode.STREAM,
            span = requestSpan
        )
        if (log.isDebugEnabled) {
            log.debug(
                "LLM stream: selected_model={}, response_chars={}, tools={} {}, " +
                    "prompt_tokens={}, completion_tokens={}, total_tokens={}, " +
                    "elapsed_ms={}, query=\"{}\"",
                modelId,
                totalChars,
                tools.size,
                tools.map { it.javaClass.simpleName },
                usage?.promptTokens ?: 0,
                usage?.completionTokens ?: 0,
                usage?.totalTokens ?: 0,
                elapsedMs,
                safeQuery.take(QUERY_LOG_CHARS)
            )
        }
        // Build via Jackson rather than string interpolation so a future
        // modelId / metric value containing a quote, backslash or newline can
        // never produce malformed SSE.
        //
        // `model` is only meaningful when the per-call Anthropic override was
        // applied — on `ollama` / `openai` profiles the underlying ChatClient
        // picks the model and `chatModelSelector.selectFor(...)` doesn't
        // reflect what actually answered the request. Omit the field rather
        // than report a misleading id.
        val payload =
            objectMapper.writeValueAsString(
                buildMap {
                    put("chars", totalChars)
                    put("elapsed_ms", elapsedMs)
                    if (anthropicActive) put("model", modelId)
                }
            )
        return Flux.just(ServerSentEvent.builder(payload).event("done").build())
    }

    private fun errorEvent(message: String): Flux<ServerSentEvent<String>> =
        Flux.just(ServerSentEvent.builder<String>(message).event("error").build())

    /**
     * SSE events for one `ChatResponse` chunk, in emission order.
     *
     * A generation turn that ends with a tool-calling finish reason is
     * narration the model produced immediately before calling tools (e.g.
     * "I'll gather the data needed for this briefing…"). That text already
     * streamed to the client as `token` events, so a `reset` event is
     * appended telling the frontend to discard its accumulated buffer — the
     * real answer starts fresh on the next turn. A turn that ends with `STOP`
     * is the final answer and streams through untouched.
     *
     * Matched case-insensitively against [TOOL_TURN_FINISH_REASONS]: DeepSeek
     * / OpenAI-style providers emit `tool_calls`; Anthropic — svc-agent's
     * default provider (`model.chat: anthropic`) — sets `finishReason` from
     * `StopReason.toString()`, whose wire value for a tool-calling turn is
     * `tool_use`, not `tool_calls`. Both must be recognised or the preamble
     * bug survives on the default routing path. In production this value
     * normally arrives via [ToolTurnBoundaryAdvisor]'s synthetic element, not
     * the provider's own chunk: Spring AI's `ToolCallingAdvisor` filters every
     * tool-call-carrying `ChatResponse` out of the stream before it reaches
     * this controller, so the real element never gets here to be read.
     *
     * [text] may be empty (Spring AI's trailing metadata-only chunk carries
     * the finish reason with no text) — in that case only the `reset` event
     * (if any) is emitted.
     *
     * A [finishReason] outside both [TOOL_TURN_FINISH_REASONS] and
     * [KNOWN_FINAL_FINISH_REASONS] is logged rather than silently ignored —
     * a provider or Spring AI version bump changing the wire value would
     * otherwise make the reset stop firing with no signal. Behavior is
     * unchanged either way: no reset for an unrecognized reason. A blank
     * (empty-string) reason is treated as absent, not unrecognized — DeepSeek
     * maps an absent `finish_reason` to `""` on EVERY streamed chunk, so
     * without this the warn would fire dozens of times per second rather
     * than only for a genuinely unexpected value.
     */
    internal fun sseEventsFor(
        text: String,
        finishReason: String?
    ): List<ServerSentEvent<String>> =
        buildList {
            if (text.isNotEmpty()) add(ServerSentEvent.builder(text).event("token").build())
            val reason = finishReason?.takeIf { it.isNotBlank() }?.lowercase()
            when {
                reason == null -> {
                    Unit
                }
                reason in TOOL_TURN_FINISH_REASONS -> {
                    add(ServerSentEvent.builder("").event("reset").build())
                }
                reason !in KNOWN_FINAL_FINISH_REASONS -> {
                    log.warn(
                        "Unrecognized finish reason '{}' — reset event not emitted; check provider wire values",
                        finishReason
                    )
                }
            }
        }

    private fun logLlmInteraction(
        userMessage: String,
        tools: Array<Any>,
        chatResponse: ChatResponse?,
        content: String,
        elapsedMs: Long,
        selectedModelId: String
    ) {
        val meta = chatResponse?.metadata
        val usage = meta?.usage
        // Sentry transaction measurements — runs even when DEBUG is off so
        // production retains queryable token telemetry. Only tag the model
        // when the per-call Anthropic override was applied; otherwise the
        // selected id doesn't reflect the actual answering model.
        llmMetrics.capture(
            modelId = selectedModelId.takeIf { anthropicActive },
            usage = usage,
            elapsedMs = elapsedMs,
            toolCount = tools.size,
            mode = LlmMetrics.Mode.CALL
        )
        if (!log.isDebugEnabled) return
        val promptPreview = userMessage.take(120).replace("\n", " ")
        val toolNames = tools.map { it.javaClass.simpleName }
        log.debug(
            "LLM call: model={}, selected_model={}, prompt_tokens={}, completion_tokens={}, total_tokens={}, " +
                "tools={} {}, response_chars={}, elapsed_ms={}, prompt_preview=\"{}\"",
            meta?.model ?: "unknown",
            selectedModelId,
            usage?.promptTokens ?: 0,
            usage?.completionTokens ?: 0,
            usage?.totalTokens ?: 0,
            tools.size,
            toolNames,
            content.length,
            elapsedMs,
            promptPreview
        )
    }

    /**
     * Assemble the user message: a current-date stamp, optional page context, then the question.
     *
     * The date line is load-bearing — the LLM has no inherent knowledge of today and otherwise
     * answers from its training-cutoff frame (DeepSeek was dating "current" events to 2025). Stamped
     * onto the user message rather than the system prompt so the static system prompt stays
     * cache-stable across requests.
     *
     * `internal` for unit testing against a fixed [clock].
     */
    internal fun buildUserMessage(request: AgentQuery): String {
        val dateLine = "[Current date: ${LocalDate.now(clock)}]"
        val ctx = request.context
        val header =
            if (ctx.isNullOrEmpty()) {
                dateLine
            } else {
                val contextLine = ctx.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                "$dateLine\n[Page context: $contextLine]"
            }
        return "$header\n\n${request.query}"
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