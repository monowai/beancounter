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
 */
@RestController
@RequestMapping("/agent")
@Tag(name = "Agent", description = "Natural-language Beancounter assistant")
class AgentController(
    private val routing: ModelRouting,
    private val healthChecker: ServiceHealthChecker,
    private val prompts: TurnPrompts,
    private val objectMapper: ObjectMapper,
    private val llmMetrics: LlmMetrics,
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val admission: TurnAdmission,
    private val planner: TurnPlanner,
    private val metering: TurnMetering,
    private val turns: ConversationTurns,
    private val inFlightTurns: InFlightTurns,
    // Dates a review's cache key. Defaulted so Spring wires it without a Clock bean; fixed in tests.
    private val clock: Clock = Clock.systemUTC(),
    private val pageQuerySelector: PageQuerySelector,
    private val reviewCache: AssetReviewCache,
    private val holdings: HoldingFactsSource
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

    private companion object {
        // Two retries with exponential backoff (+jitter) cover the common
        // sub-second connectivity blip without holding the request open long
        // enough to outlast a real outage — that surfaces as the error envelope.
        const val STREAM_RETRY_ATTEMPTS = 2L
        val STREAM_RETRY_MIN_BACKOFF: Duration = Duration.ofMillis(250)

        // Escapes markup only: a label's dash is not an entity.
        const val UTF_8 = "UTF-8"

        // Leading chars of the user's question kept in a log line — enough to
        // identify the request in kauri's logs without shipping the whole
        // question (which can carry page context the user typed).
        const val QUERY_LOG_CHARS = 120
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
                Opening.Failed(TurnFailure.NOT_HELD)
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
            val code = TurnFailure.classify(e)
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
            planner.withQuestion(
                AgentQuery(
                    context = pageQuerySelector.assetReviewContext(asset),
                    think = request.think,
                    deepThink = request.deepThink
                )
            )
        val key = AssetReviewKey(asset.market, asset.ticker, reviewDate, routing.answeringModel(turn))
        val hit = planner.cachedReview(key, request.refresh)
        if (hit != null) return hit
        val answer = prompts.ask(turn)
        if (answer.content.isBlank()) throw UnansweredTurn(TurnFailure.emptyAnswerCode(answer.finishReason))
        reviewCache.store(key, answer.content)
        return answer.content
    }

    /** A failed `/query`, in the shape every failure takes. */
    private fun failure(
        safeQuery: String,
        code: String
    ): ResponseEntity<AgentResponse> =
        ResponseEntity
            .status(TurnFailure.statusFor(code))
            .body(
                AgentResponse(
                    query = safeQuery,
                    response = TurnFailure.messageFor(code),
                    timestamp = Instant.now().toString(),
                    error = code
                )
            )

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
            is Admission.NoLlm -> noLlm(admission.request)
            is Admission.Refused -> failure(HtmlUtils.htmlEscape(admission.label, UTF_8), admission.code)
            is Admission.Admitted -> answerQuery(admission)
        }
    }

    /** The `/query` answer when no LLM is configured. */
    private fun noLlm(request: AgentQuery): ResponseEntity<AgentResponse> =
        ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                AgentResponse(
                    query = HtmlUtils.htmlEscape(request.query),
                    response =
                        "No LLM is configured. Set ${AgentProvider.PROPERTY} to '${AgentProvider.DEEPSEEK}', " +
                            "'${AgentProvider.ANTHROPIC}', '${AgentProvider.OPENAI}', or '${AgentProvider.OLLAMA}'.",
                    timestamp = Instant.now().toString(),
                    error = TurnFailure.NO_LLM
                )
            )

    /** A `/query` turn the caller may run: its opening built first, when it has one. */
    private fun answerQuery(admitted: Admission.Admitted): ResponseEntity<AgentResponse> {
        val plan = admitted.plan
        val request = plan.request
        return when (val opening = plan.opening?.let { holdingOpening(request, it) } ?: Opening.Ready(request)) {
            // Echo what the caller asked about: the opening question was never built.
            is Opening.Failed -> {
                failure(HtmlUtils.htmlEscape(plan.label, UTF_8), opening.code)
            }
            is Opening.Ready -> {
                answerTurn(opening.turn, admitted)
            }
        }
    }

    /** [asked], recorded in its conversation, answered from the cached review or live. */
    private fun answerTurn(
        asked: AgentQuery,
        admitted: Admission.Admitted
    ): ResponseEntity<AgentResponse> {
        val (_, reviewKey, hit, meter) = admitted
        val safeQuery = HtmlUtils.htmlEscape(asked.query)
        val (turn, conversationRecorder) = turns.open(asked, admitted.plan.thread)
        if (reviewKey != null && hit != null) {
            // No model runs: the stored answer is recorded as a live one would be, and not counted.
            llmMetrics.cached(LlmMetrics.Mode.CALL, reviewKey.model)
            turns.recordQuietly { conversationRecorder.answered(hit) }
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
            metering.metered(
                reviewKey?.let { ReviewCachingRecorder(conversationRecorder, reviewCache, it) } ?: conversationRecorder,
                meter
            )
        return askLive(turn, safeQuery, recorder)
    }

    /** [turn] asked of the model, its answer or failure recorded and returned. */
    private fun askLive(
        turn: AgentQuery,
        safeQuery: String,
        recorder: TurnRecorder
    ): ResponseEntity<AgentResponse> =
        try {
            answered(prompts.ask(turn), safeQuery, recorder)
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
            val errorCode = TurnFailure.classify(e)
            log.error("Agent query failed ({}): {}", errorCode, e.message, e)
            turns.recordQuietly { recorder.failed(errorCode) }
            failure(safeQuery, errorCode)
        }

    /** The model's [answer] to a `/query` turn, recorded; a blank one is a failure. */
    private fun answered(
        answer: ModelAnswer,
        safeQuery: String,
        recorder: TurnRecorder
    ): ResponseEntity<AgentResponse> {
        val content = answer.content

        // Same hole the streaming path had: a turn can succeed at the HTTP
        // level and still say nothing (typically after the context window
        // filled with tool results). Answering with a blank body left every
        // caller rendering an empty response and no reason for it.
        if (content.isBlank()) {
            val code = TurnFailure.emptyAnswerCode(answer.finishReason)
            log.warn(
                "Agent query produced no answer: code={}, model={}, query=\"{}\"",
                code,
                answer.modelId,
                safeQuery.take(QUERY_LOG_CHARS)
            )
            turns.recordQuietly { recorder.failed(code) }
            return failure(safeQuery, code)
        }

        turns.recordQuietly { recorder.answered(content) }
        return ResponseEntity.ok(
            AgentResponse(
                query = safeQuery,
                response = content,
                timestamp = Instant.now().toString()
            )
        )
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
        return when (val admission = admission.admit(body)) {
            // A code, not prose: the streaming path's error payload is a contract
            // the UI renders copy from, and `/query` already answers this case
            // with the same NO_LLM code.
            is Admission.NoLlm -> errorEvent(TurnFailure.NO_LLM)
            is Admission.Refused -> errorEvent(admission.code)
            is Admission.Admitted -> turnStream(admission)
        }
    }

    /**
     * The pipeline for a turn [stream] has let run: cold for a stateless
     * request, hot and connected here for a conversation (see [stream]).
     */
    private fun turnStream(admitted: Admission.Admitted): Flux<ServerSentEvent<String>> {
        val plan = admitted.plan
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
                    val (turn, recorder) = turns.open(plan.request, plan.thread)
                    pipelineFor(admitted, turn, recorder)
                } else {
                    openingPipeline(plan.request, subject, plan.thread, admitted.meter)
                }
            } catch (e: Exception) {
                conversationId?.let(inFlightTurns::end)
                throw e
            }
        // doFinally sits inside the hot boundary, so it fires when the
        // pipeline itself completes or fails — after the record{} write that
        // ends it — and never on the subscriber's cancel. replay() keeps the
        // tokens emitted before the MVC emitter subscribes (it attaches after
        // this method returns); autoConnect(0) subscribes right here, on the
        // request thread, so contextCapture still snapshots the caller's
        // SecurityContext, and unlike refCount it never disconnects when the
        // downstream count drops to zero.
        return if (conversationId == null) {
            pipeline
        } else {
            pipeline
                .doFinally { inFlightTurns.end(conversationId) }
                .replay()
                .autoConnect(0)
        }
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
                        val (turn, recorder) = turns.open(opening.turn, thread)
                        answerPipeline(turn, metering.metered(recorder, meter))
                    }
                }
            }.onErrorResume { e ->
                val code = TurnFailure.classify(e)
                log.error("Holding analysis stream failed ({}): {}", code, e.message, e)
                errorEvent(code)
            }.contextCapture()

    /**
     * A canned Asset Review answered from the cached review when one is fresh,
     * uncounted; else live, keeping a successful answer under its key for next
     * time and counting it against the caller's meter.
     */
    private fun pipelineFor(
        admitted: Admission.Admitted,
        turn: AgentQuery,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val (_, key, hit, meter) = admitted
        if (key != null && hit != null) return cachedPipeline(hit, key, recorder)
        val live = key?.let { ReviewCachingRecorder(recorder, reviewCache, it) } ?: recorder
        return answerPipeline(turn, metering.metered(live, meter))
    }

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
                Flux.just(
                    donePayload(
                        content.length.toLong(),
                        System.currentTimeMillis() - startMs,
                        routing.reported(key.model),
                        cached = true
                    )
                )
            }
        return Flux
            .just(ServerSentEvent.builder(content).event("token").build())
            .concatWith(turns.record { recorder.answered(content) }.thenMany(Flux.empty()))
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
                val code = TurnFailure.classify(e)
                turns.record { recorder.failed(code) }.thenMany(errorEvent(code))
            }.contextCapture()

    private fun runStream(
        request: AgentQuery,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val (call, responses) = prompts.stream(request)
        val tally = StreamTally()
        val tokenEvents =
            responses
                .concatMap { resp -> Flux.fromIterable(tally.accept(resp)) }
        // Transient connectivity blips (e.g. a sub-second DNS failure resolving
        // api.deepseek.com) surface as WebClientRequestException at connect time
        // — before any token is emitted. Retry those rather than failing the
        // user's request. The `chars == 0` guard makes retries safe: once
        // content has streamed to the client, re-running would duplicate it, so
        // a later drop is allowed to propagate to the error envelope.
        val retriedTokens =
            tokenEvents.retryWhen(
                Retry
                    .backoff(STREAM_RETRY_ATTEMPTS, STREAM_RETRY_MIN_BACKOFF)
                    .filter { e -> tally.chars == 0L && TurnFailure.isTransientConnectivity(e) }
                    // Surface the original cause, not Reactor's RetryExhausted
                    // wrapper, so classification still maps it accurately.
                    .onRetryExhaustedThrow { _, signal -> signal.failure() }
            )
        return retriedTokens
            .concatWith(Flux.defer { answerOrFailure(call, tally, recorder) })
            .concatWith(Flux.defer { doneEvent(call, tally) })
    }

    /**
     * The end of a streamed turn: its answer recorded, or — when it ended
     * without one — a failure the client can't otherwise see: HTTP was 200,
     * no exception was thrown, and the `done` envelope carries metadata only,
     * so the UI would render an empty assistant bubble and say nothing about
     * why. Classified and emitted as a normal error event, using the same
     * codes both transports share.
     */
    private fun answerOrFailure(
        call: ModelCall,
        tally: StreamTally,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val answer = tally.answer
        if (answer.isNotEmpty()) return turns.record { recorder.answered(answer) }.thenMany(Flux.empty())
        val code = TurnFailure.emptyAnswerCode(tally.finishReason)
        log.warn(
            "Agent stream produced no answer: code={}, finish_reason={}, model={}, query=\"{}\"",
            code,
            tally.finishReason,
            call.modelId,
            call.query.take(QUERY_LOG_CHARS)
        )
        return turns.record { recorder.failed(code) }.thenMany(errorEvent(code))
    }

    private fun doneEvent(
        call: ModelCall,
        tally: StreamTally
    ): Flux<ServerSentEvent<String>> {
        llmMetrics.completed(call, tally.usage, tally.chars, LlmMetrics.Mode.STREAM)
        // Build via Jackson rather than string interpolation so a future
        // modelId / metric value containing a quote, backslash or newline can
        // never produce malformed SSE. `model` is only present where the
        // per-call override names what actually answered.
        return Flux.just(donePayload(tally.chars, call.elapsedMs, call.reportedModelId))
    }

    /** The `done` event closing a streamed turn: its size, how long it took, and what answered. */
    private fun donePayload(
        chars: Long,
        elapsedMs: Long,
        model: String?,
        cached: Boolean = false
    ): ServerSentEvent<String> {
        val payload =
            objectMapper.writeValueAsString(
                buildMap {
                    put("chars", chars)
                    put("elapsed_ms", elapsedMs)
                    model?.let { put("model", it) }
                    if (cached) put("cached", true)
                }
            )
        return ServerSentEvent.builder(payload).event("done").build()
    }

    private fun errorEvent(message: String): Flux<ServerSentEvent<String>> =
        Flux.just(ServerSentEvent.builder<String>(message).event("error").build())
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