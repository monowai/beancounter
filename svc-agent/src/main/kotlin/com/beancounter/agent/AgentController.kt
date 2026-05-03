package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.tools.ToolSelector
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicCacheOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Autowired
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
import java.time.Instant
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
    @Autowired(required = false) private val anthropicCacheOptions: AnthropicCacheOptions?,
    private val healthChecker: ServiceHealthChecker,
    private val toolSelector: ToolSelector,
    private val systemPromptSelector: SystemPromptSelector,
    private val chatModelSelector: ChatModelSelector,
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
    private val llmMetrics: LlmMetrics,
    private val scopeAuthorizer: AgentScopeAuthorizer
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

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
     * `deepThink` raises `maxTokens` so the deep tier (deepseek-reasoner /
     * claude-opus-*) has headroom for chain-of-thought + final answer; on the
     * Anthropic surface it also explicitly enables thinking with a 4k budget
     * (Claude 4 has thinking on by default; setting it explicitly documents
     * intent and lets the budget be tuned).
     */
    internal fun buildOptions(
        modelId: String,
        deepThink: Boolean
    ): org.springframework.ai.chat.prompt.ChatOptions? =
        when {
            anthropicActive -> {
                val b = AnthropicChatOptions.builder().model(modelId)
                anthropicCacheOptions?.let(b::cacheOptions)
                if (deepThink) {
                    b
                        .maxTokens(16384)
                        .thinking(
                            org.springframework.ai.anthropic.api.AnthropicApi.ThinkingType.ENABLED,
                            4096
                        )
                }
                b.build()
            }
            deepseekActive -> {
                org.springframework.ai.deepseek.DeepSeekChatOptions
                    .builder()
                    .model(modelId)
                    .maxTokens(if (deepThink) 16384 else 4096)
                    .build()
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
                        error = "no-llm"
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
                chatClient
                    .prompt()
                    .system(systemPrompt)
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
        } catch (e: Exception) {
            log.error("Agent query failed: {}", e.message, e)
            ResponseEntity
                .status(500)
                .body(
                    AgentResponse(
                        query = safeQuery,
                        response = "The agent failed to process the request.",
                        timestamp = Instant.now().toString(),
                        error = "agent-error"
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
        if (chatClient == null) return errorEvent("No LLM is configured.")
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
     * Map an upstream exception into a stable, opaque SSE error code. The
     * client is free to render a friendly message keyed off the code; the
     * raw exception text never reaches the client.
     *
     *   `provider-quota`   — Anthropic credit balance exhausted (HTTP 400
     *                        invalid_request_error with "credit balance").
     *   `provider-rate`    — Provider rate-limited the request (HTTP 429).
     *   `provider-timeout` — Upstream took too long.
     *   `agent-error`      — Anything else.
     */
    internal fun classifyError(e: Throwable): String {
        val message = e.message.orEmpty()
        // Anthropic returns 400 with the credit-balance text in the body.
        // Match on the body string rather than the status code so we don't
        // accidentally match unrelated 400s.
        val isCreditBalance = message.contains("credit balance", ignoreCase = true)
        val isBilling400 =
            message.contains("billing", ignoreCase = true) &&
                message.contains("400", ignoreCase = true)
        if (isCreditBalance || isBilling400) {
            return "provider-quota"
        }
        if (message.contains("429") ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("rate_limit", ignoreCase = true)
        ) {
            return "provider-rate"
        }
        if (e is java.util.concurrent.TimeoutException ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)
        ) {
            return "provider-timeout"
        }
        return "agent-error"
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
                .doOnNext { resp -> resp.metadata.usage?.let { capturedUsage.set(it) } }
                // Spring AI emits trailing ChatResponse chunks with no Generation
                // (metadata-only — token usage, finishReason). Skip those instead
                // of NPE'ing on resp.result.
                .map { resp ->
                    resp.result
                        ?.output
                        ?.text
                        .orEmpty()
                }.filter { it.isNotEmpty() }
                .map { chunk ->
                    totalChars.addAndGet(chunk.length.toLong())
                    ServerSentEvent.builder(chunk).event("token").build()
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
        return tokenEvents.concatWith(doneEvent)
    }

    private fun buildStreamSpec(
        request: AgentQuery,
        tools: Array<Any>,
        modelId: String
    ): ChatClient.StreamResponseSpec {
        val promptSpec =
            chatClient!!
                .prompt()
                .system(systemPromptSelector.selectFor(request.context))
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

    private fun buildUserMessage(request: AgentQuery): String {
        val ctx = request.context
        if (ctx.isNullOrEmpty()) return request.query
        val contextLine = ctx.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return "[Page context: $contextLine]\n\n${request.query}"
    }
}

data class AgentQuery(
    val query: String,
    val context: Map<String, Any>? = null,
    /**
     * Caller-driven escalation to the deep tier (typically `deepseek-reasoner`
     * or `claude-opus-*`). Off by default; flips model selection regardless of
     * page-context routing in [ChatModelSelector].
     */
    val deepThink: Boolean = false
)

data class AgentResponse(
    val query: String,
    val response: String,
    val timestamp: String,
    val error: String? = null
)