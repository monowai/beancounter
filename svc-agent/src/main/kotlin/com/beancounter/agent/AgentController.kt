package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.tools.ToolSelector
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
    // UTC clock for stamping the current date onto each user message. Defaulted so Spring wires it
    // without a Clock bean; overridden in tests for a fixed date.
    private val clock: Clock = Clock.systemUTC()
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

        // Stable client-facing failure codes. bc-view keys its copy off these
        // (see lib/utils/agent/agentErrors.ts); renaming one is a contract change.
        const val NO_LLM = "no-llm"
        const val PROVIDER_QUOTA = "provider-quota"
        const val PROVIDER_RATE = "provider-rate"
        const val PROVIDER_TIMEOUT = "provider-timeout"
        const val AGENT_ERROR = "agent-error"

        const val BAD_REQUEST = 400
        const val PAYMENT_REQUIRED = 402
        const val TOO_MANY_REQUESTS = 429
        const val INTERNAL_ERROR = 500
        const val GATEWAY_TIMEOUT = 504

        // Only a status at the very start of the message is trusted — that is
        // where Spring AI's "<status> - <body>" puts it. Digit boundaries stop
        // a number inside a tool result from reading as a status.
        val LEADING_HTTP_STATUS = Regex("""^\s*(\d{3})(?!\d)""")
        const val CAUSE_CHAIN_DEPTH = 5

        // Finish reasons that mark a turn as narration-before-tool-calls,
        // lower-cased for case-insensitive matching in sseEventsFor.
        // DeepSeek / OpenAI-style providers emit "tool_calls"; Anthropic
        // (svc-agent's default — model.chat: anthropic) sets finishReason
        // from StopReason.toString(), whose wire value is "tool_use". Do NOT
        // add "pause_turn" or any other Anthropic stop reason here.
        val TOOL_TURN_FINISH_REASONS = setOf("tool_calls", "tool_use")

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

    /**
     * Anthropic-only feature: the per-call model override uses
     * [AnthropicChatOptions]. When the active profile is `ollama` or `openai`,
     * skip the override and let the configured ChatClient's default model run.
     */
    private val anthropicActive: Boolean
        get() {
            val profiles = environment.activeProfiles.toSet()
            return "ollama" !in profiles &&
                "openai" !in profiles &&
                "deepseek" !in profiles
        }

    private val deepseekActive: Boolean
        get() = "deepseek" in environment.activeProfiles.toSet()

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
     */
    internal fun buildOptions(
        modelId: String,
        deepThink: Boolean
    ): org.springframework.ai.chat.prompt.ChatOptions.Builder<*>? =
        when {
            anthropicActive -> {
                val b = AnthropicChatOptions.builder().model(modelId)
                anthropicCacheOptions?.let(b::cacheOptions)
                if (deepThink) {
                    b
                        .maxTokens(16384)
                        // Spring AI 2.0: the two-arg thinking(type, budget) is
                        // gone; thinkingEnabled(budgetTokens) is the explicit
                        // enable + budget call (4k budget preserved).
                        .thinkingEnabled(4096L)
                }
                b
            }
            deepseekActive -> {
                org.springframework.ai.deepseek.DeepSeekChatOptions
                    .builder()
                    .model(modelId)
                    .maxTokens(if (deepThink) 16384 else 4096)
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
                "Beancounter REST APIs. It chooses which to invoke."
    )
    fun query(
        @RequestBody request: AgentQuery
    ): ResponseEntity<AgentResponse> {
        scopeAuthorizer.authorize(request.context)
        val safeQuery = HtmlUtils.htmlEscape(request.query)
        if (chatClient == null) {
            return ResponseEntity
                .status(503)
                .body(
                    AgentResponse(
                        query = safeQuery,
                        response = "No LLM is configured. Set the 'ollama', 'openai', or 'anthropic' Spring profile.",
                        timestamp = Instant.now().toString(),
                        error = NO_LLM
                    )
                )
        }

        return try {
            val userMessage = buildUserMessage(request)
            val tools = toolSelector.selectTools(request.context)
            val systemPrompt = systemPromptSelector.selectFor(request.context)
            val modelId = chatModelSelector.selectFor(request.context, request.deepThink)
            val startMs = System.currentTimeMillis()
            val promptSpec =
                clientFor(request)
                    .prompt()
                    .system(systemPrompt)
                    .messages(historyMessages(request))
                    .user(userMessage)
                    .tools(*tools)
            // Per-call options REPLACE (not merge) the ChatClient's default
            // options — Anthropic cache config must be re-applied here, or
            // every request silently loses prompt caching. See buildOptions.
            val callResponse =
                buildOptions(modelId, request.deepThink)?.let { opts ->
                    promptSpec.options(opts).call()
                } ?: promptSpec.call()

            val chatResponse = callResponse.chatResponse()
            val content = chatResponse?.result?.output?.text ?: callResponse.content() ?: "(empty response)"
            val elapsedMs = System.currentTimeMillis() - startMs

            logLlmInteraction(userMessage, tools, chatResponse, content, elapsedMs, modelId)

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
            ResponseEntity
                .status(statusFor(errorCode))
                .body(
                    AgentResponse(
                        query = safeQuery,
                        response = messageFor(errorCode),
                        timestamp = Instant.now().toString(),
                        error = errorCode
                    )
                )
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
        @RequestBody request: AgentQuery
    ): Flux<ServerSentEvent<String>> {
        scopeAuthorizer.authorize(request.context)
        // A code, not prose: the streaming path's error payload is a contract
        // the UI renders copy from, and `/query` already answers this case
        // with the same NO_LLM code.
        if (chatClient == null) return errorEvent(NO_LLM)
        // Wrap the pipeline in Flux.defer so setup-time exceptions (selector
        // failures, options builder failures) become Flux errors and reach
        // onErrorResume rather than escaping out of the controller as a 500.
        //
        // .contextCapture() snapshots the request thread's ThreadLocals
        // (incl. SecurityContext via SecurityContextPropagationConfig) so
        // tool callbacks invoked on Reactor scheduler threads still see the
        // caller's JWT — without it, TokenService.jwt would throw
        // "Not authorised" on every tool call.
        return Flux
            .defer { runStream(request) }
            .onErrorResume { e ->
                // Never return e.message — leaks internals. Log full detail
                // server-side; client gets a stable, classified code so the
                // UI can surface a real cause instead of generic
                // "agent-error" when the failure is something the user can
                // act on (e.g. provider quota, rate limit).
                log.error("Agent stream failed: {}", e.message, e)
                errorEvent(classifyError(e))
            }.contextCapture()
    }

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
            PROVIDER_RATE -> TOO_MANY_REQUESTS
            PROVIDER_TIMEOUT -> GATEWAY_TIMEOUT
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
            else -> {
                "The agent failed to process the request."
            }
        }

    private fun runStream(request: AgentQuery): Flux<ServerSentEvent<String>> {
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
                    if (text.isNotEmpty()) totalChars.addAndGet(text.length.toLong())
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
        return retriedTokens.concatWith(doneEvent)
    }

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
     * `think=false`) use the non-thinking [fastChatClient] for lowest latency;
     * the interactive Chat FAB sets `think=true` to keep DeepSeek thinking mode.
     * Falls back to the thinking client if no fast client is configured.
     * Callers guard `chatClient != null` first, so the result is non-null.
     */
    private fun clientFor(request: AgentQuery): ChatClient =
        (if (request.think) chatClient else fastChatClient ?: chatClient)!!

    private fun buildStreamSpec(
        request: AgentQuery,
        tools: Array<Any>,
        modelId: String
    ): ChatClient.StreamResponseSpec {
        val promptSpec =
            clientFor(request)
                .prompt()
                .system(systemPromptSelector.selectFor(request.context))
                .messages(historyMessages(request))
                .user(buildUserMessage(request))
                .tools(*tools)
        return buildOptions(modelId, request.deepThink)?.let { opts ->
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
                safeQuery.take(120)
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
     * bug survives on the default routing path.
     *
     * [text] may be empty (Spring AI's trailing metadata-only chunk carries
     * the finish reason with no text) — in that case only the `reset` event
     * (if any) is emitted.
     *
     * A [finishReason] outside both [TOOL_TURN_FINISH_REASONS] and
     * [KNOWN_FINAL_FINISH_REASONS] is logged rather than silently ignored —
     * a provider or Spring AI version bump changing the wire value would
     * otherwise make the reset stop firing with no signal. Behavior is
     * unchanged either way: no reset for an unrecognized reason. Finish
     * reasons arrive once per turn, so this can't spam the log.
     */
    internal fun sseEventsFor(
        text: String,
        finishReason: String?
    ): List<ServerSentEvent<String>> =
        buildList {
            if (text.isNotEmpty()) add(ServerSentEvent.builder(text).event("token").build())
            val reason = finishReason?.lowercase()
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
    val query: String,
    val context: Map<String, Any>? = null,
    /**
     * Enable DeepSeek thinking mode for this request. Defaults to `false` so
     * pre-canned prompts get the fastest (non-thinking) response; the interactive
     * Chat FAB sets `true`. Orthogonal to [deepThink] (which escalates the model
     * tier). Routes to the non-thinking `fastChatClient` when `false`.
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
    val history: List<ChatTurn>? = null
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
    val error: String? = null
)