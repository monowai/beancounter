package com.beancounter.agent

import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.tools.ToolSelector
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils
import java.time.Instant

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
    private val healthChecker: ServiceHealthChecker,
    private val toolSelector: ToolSelector,
    private val systemPromptSelector: SystemPromptSelector
) {
    private val log = LoggerFactory.getLogger(AgentController::class.java)

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
            val startMs = System.currentTimeMillis()
            val callResponse =
                chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(*tools)
                    .call()

            val chatResponse = callResponse.chatResponse()
            val content = chatResponse?.result?.output?.text ?: callResponse.content() ?: "(empty response)"
            val elapsedMs = System.currentTimeMillis() - startMs

            logLlmInteraction(userMessage, tools, chatResponse, content, elapsedMs)

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

    private fun logLlmInteraction(
        userMessage: String,
        tools: Array<Any>,
        chatResponse: ChatResponse?,
        content: String,
        elapsedMs: Long
    ) {
        if (!log.isDebugEnabled) return
        val meta = chatResponse?.metadata
        val usage = meta?.usage
        val promptPreview = userMessage.take(120).replace("\n", " ")
        val toolNames = tools.map { it.javaClass.simpleName }
        log.debug(
            "LLM call: model={}, prompt_tokens={}, completion_tokens={}, total_tokens={}, " +
                "tools={} {}, response_chars={}, elapsed_ms={}, prompt_preview=\"{}\"",
            meta?.model ?: "unknown",
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