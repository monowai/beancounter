package com.beancounter.agent

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
    private val objectMapper: ObjectMapper
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
            return "ollama" !in profiles && "openai" !in profiles
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
            val modelId = chatModelSelector.selectFor(request.context)
            val startMs = System.currentTimeMillis()
            val promptSpec =
                chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(*tools)
            val callResponse =
                if (anthropicActive) {
                    // Per-call options REPLACE (not merge) the ChatClient's
                    // default options — so the cache config configured in
                    // ChatClientConfiguration must be re-applied here, or
                    // every request silently loses prompt caching.
                    val optionsBuilder = AnthropicChatOptions.builder().model(modelId)
                    anthropicCacheOptions?.let(optionsBuilder::cacheOptions)
                    promptSpec.options(optionsBuilder.build()).call()
                } else {
                    promptSpec.call()
                }

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
     * idle-timeout, surfacing as "Load failed" in [useChat]).
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
        if (chatClient == null) return errorEvent("No LLM is configured.")
        // Wrap the pipeline in Flux.defer so setup-time exceptions (selector
        // failures, options builder failures) become Flux errors and reach
        // onErrorResume rather than escaping out of the controller as a 500.
        return Flux
            .defer { runStream(request) }
            .onErrorResume { e ->
                // Never return e.message — leaks internals. Log full detail
                // server-side; client gets a stable opaque code.
                log.error("Agent stream failed: {}", e.message, e)
                errorEvent("agent-error")
            }
    }

    private fun runStream(request: AgentQuery): Flux<ServerSentEvent<String>> {
        val safeQuery = HtmlUtils.htmlEscape(request.query)
        val tools = toolSelector.selectTools(request.context)
        val modelId = chatModelSelector.selectFor(request.context)
        val startMs = System.currentTimeMillis()

        // Track byte count for the final log line — Spring AI's stream() Flux
        // doesn't surface token usage on per-chunk events, so we count chars
        // as a proxy for visibility.
        val totalChars = AtomicLong(0)

        val streamSpec = buildStreamSpec(request, tools, modelId)
        val tokenEvents =
            streamSpec
                .content()
                .map { chunk ->
                    totalChars.addAndGet(chunk.length.toLong())
                    ServerSentEvent.builder(chunk).event("token").build()
                }
        val doneEvent =
            Flux.defer { doneEvent(modelId, tools, totalChars.get(), startMs, safeQuery) }
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
        return if (anthropicActive) {
            val builder = AnthropicChatOptions.builder().model(modelId)
            anthropicCacheOptions?.let(builder::cacheOptions)
            promptSpec.options(builder.build()).stream()
        } else {
            promptSpec.stream()
        }
    }

    private fun doneEvent(
        modelId: String,
        tools: Array<Any>,
        totalChars: Long,
        startMs: Long,
        safeQuery: String
    ): Flux<ServerSentEvent<String>> {
        val elapsedMs = System.currentTimeMillis() - startMs
        if (log.isDebugEnabled) {
            log.debug(
                "LLM stream: selected_model={}, response_chars={}, tools={} {}, " +
                    "elapsed_ms={}, query=\"{}\"",
                modelId,
                totalChars,
                tools.size,
                tools.map { it.javaClass.simpleName },
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
        if (!log.isDebugEnabled) return
        val meta = chatResponse?.metadata
        val usage = meta?.usage
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
    val context: Map<String, Any>? = null
)

data class AgentResponse(
    val query: String,
    val response: String,
    val timestamp: String,
    val error: String? = null
)