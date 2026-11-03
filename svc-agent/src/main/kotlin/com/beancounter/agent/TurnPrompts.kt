package com.beancounter.agent

import com.beancounter.agent.tools.ToolSelector
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils
import reactor.core.publisher.Flux
import java.time.Clock
import java.time.LocalDate

/** One blocking model answer and why it ended. */
data class ModelAnswer(
    val content: String,
    val finishReason: String?,
    val modelId: String
)

/** A streamed model answer, chunk by chunk, and the call that produced it. */
data class ModelStream(
    val call: ModelCall,
    val responses: Flux<ChatResponse>
)

/**
 * Assembles a turn's prompt — the page's system prompt, replayed history, the
 * dated question and the page's tools — and asks it of the model [ModelRouting]
 * picks, blocking ([ask]) or streamed ([stream]).
 */
@Component
class TurnPrompts(
    private val toolSelector: ToolSelector,
    private val systemPromptSelector: SystemPromptSelector,
    private val routing: ModelRouting,
    private val llmMetrics: LlmMetrics,
    // UTC clock for stamping the current date onto each user message. Defaulted so Spring wires it
    // without a Clock bean; overridden in tests for a fixed date.
    private val clock: Clock = Clock.systemUTC()
) {
    companion object {
        // Caller-supplied history is trusted only up to this many trailing
        // turns — bounds the tokens a single request can push into the
        // prompt regardless of what the client sends.
        const val MAX_HISTORY_TURNS = 6
    }

    /** A prompt ready to send, and the call it will be measured as. */
    private class Prepared(
        val call: ModelCall,
        val spec: ChatClient.ChatClientRequestSpec
    )

    /** Ask the model [turn] and wait for the answer. */
    fun ask(turn: AgentQuery): ModelAnswer {
        val prepared = prepare(turn)
        val callResponse = prepared.spec.call()
        val chatResponse = callResponse.chatResponse()
        val content = chatResponse?.result?.output?.text ?: callResponse.content() ?: ""
        llmMetrics.completed(
            prepared.call,
            chatResponse?.metadata?.usage,
            content.length.toLong(),
            LlmMetrics.Mode.CALL
        )
        return ModelAnswer(content, chatResponse?.result?.metadata?.finishReason, prepared.call.modelId)
    }

    /** Ask the model [turn], its answer streamed as it is written. */
    fun stream(turn: AgentQuery): ModelStream {
        val prepared = prepare(turn)
        return ModelStream(prepared.call, prepared.spec.stream().chatResponse())
    }

    /**
     * The prompt for [turn] on the model routed to it. Per-call options
     * REPLACE (not merge) the ChatClient's default options — see
     * [ModelRouting.buildOptions], which re-applies Anthropic's cache config.
     */
    private fun prepare(turn: AgentQuery): Prepared {
        val tools = toolSelector.selectTools(turn.context)
        val model = routing.modelFor(turn)

        // ChatClient.tools(Object...) has no List overload
        @Suppress("SpreadOperator")
        val spec =
            model.client
                .prompt()
                .system(systemPromptFor(turn))
                .messages(historyMessages(turn))
                .user(buildUserMessage(turn))
                .tools(*tools)
        val call =
            ModelCall(
                modelId = model.modelId,
                reportedModelId = model.reportedModelId,
                tools = tools.map { it.javaClass.simpleName },
                query = HtmlUtils.htmlEscape(turn.query)
            )
        return Prepared(call, model.options?.let { spec.options(it) } ?: spec)
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