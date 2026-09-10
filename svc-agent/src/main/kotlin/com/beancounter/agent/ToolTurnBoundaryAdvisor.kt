package com.beancounter.agent

import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Re-marks the end of a tool-calling turn after Spring AI's [ToolCallingAdvisor]
 * strips it out of the stream.
 *
 * `ToolCallingAdvisor.streamWithToolCallResponses` ends with
 * `.filter(ccr -> !toolExecutionEligibilityChecker.isToolCallResponse(ccr.chatResponse()))`,
 * and the default checker is `chatResponse.hasToolCalls()`. Every `ChatResponse`
 * that carries tool calls — the ONLY place a `tool_calls` / `tool_use` finish
 * reason ever appears — is therefore removed before it reaches
 * `ChatClient…stream().chatResponse()`. Code downstream that keys off that finish
 * reason (see [AgentController.sseEventsFor]) never sees it, no matter what the
 * provider sent.
 *
 * [ToolCallingAdvisor.internalStream] gives each turn its own `chainCopy` and
 * re-invokes advisors ordered after it once per turn, before its own outer
 * filter runs. Ordered one step after [ToolCallingAdvisor] (see [getOrder]),
 * this advisor sits at exactly that point: it watches the turn's raw elements —
 * including the tool-call-carrying one the outer filter is about to drop — and,
 * if any carried tool calls, appends a synthetic element that (a) has no tool
 * calls itself so it survives the filter, (b) carries empty text and a
 * `finishReason` of "tool_calls" that downstream code can key on, and (c) is
 * harmless to `ChatClientMessageAggregator`'s recursion decision, which
 * aggregates all elements of the turn into one generation and already collected
 * `toolCalls` from the real element.
 *
 * Provider-agnostic: DeepSeek/OpenAI-style providers emit per-chunk tool-call
 * deltas across several elements, Anthropic emits one terminal `message_delta`
 * element — either way, [getOrder] means we run once per turn and see them all,
 * and only need to have observed ONE tool-call-carrying element to mark the
 * boundary. [AgentController.sseEventsFor] turns the synthetic element into the
 * SSE `reset` event that tells the frontend to drop narration buffered before
 * the tool calls.
 */
class ToolTurnBoundaryAdvisor : StreamAdvisor {
    companion object {
        /**
         * The finish reason stamped on the synthetic boundary element.
         * [AgentController.TOOL_TURN_FINISH_REASONS] must contain this value
         * for the element to become an SSE `reset` event — it references this
         * constant so the contract has a single source of truth.
         */
        const val BOUNDARY_FINISH_REASON = "tool_calls"
    }

    override fun getName(): String = "ToolTurnBoundaryAdvisor"

    // Immediately after ToolCallingAdvisor so this runs INSIDE the tool-call
    // loop (re-invoked once per turn via chainCopy) and sees each turn's raw
    // elements before ToolCallingAdvisor's own outer filter strips the
    // tool-call-carrying one.
    override fun getOrder(): Int = ToolCallingAdvisor.DEFAULT_ORDER + 1

    override fun adviseStream(
        chatClientRequest: ChatClientRequest,
        streamAdvisorChain: StreamAdvisorChain
    ): Flux<ChatClientResponse> {
        // Fresh per invocation: adviseStream is called anew for every turn (the
        // per-turn chainCopy re-walks the advisor chain), so this flag never
        // leaks state across turns or concurrent requests.
        val sawToolCall = AtomicBoolean(false)
        return streamAdvisorChain
            .nextStream(chatClientRequest)
            .doOnNext { response ->
                if (response.chatResponse()?.hasToolCalls() == true) sawToolCall.set(true)
            }.concatWith(
                // Deferred so the flag is read only after the upstream turn has
                // fully completed and every doOnNext has run.
                Flux.defer {
                    if (sawToolCall.get()) {
                        Flux.just(boundaryElement(chatClientRequest))
                    } else {
                        Flux.empty()
                    }
                }
            )
    }

    /**
     * A [ChatClientResponse] that marks "this turn ended in tool calls" without
     * itself carrying any — empty text, no [AssistantMessage.ToolCall]s, so
     * `hasToolCalls()` is false and it survives [ToolCallingAdvisor]'s filter.
     * Context is carried over from the request so downstream advisors that read
     * it see the same execution-chain state as every other element in the turn.
     */
    private fun boundaryElement(chatClientRequest: ChatClientRequest): ChatClientResponse {
        val generation =
            Generation(
                AssistantMessage(""),
                ChatGenerationMetadata.builder().finishReason(BOUNDARY_FINISH_REASON).build()
            )
        return ChatClientResponse
            .builder()
            .chatResponse(ChatResponse(listOf(generation)))
            .context(chatClientRequest.context())
            .build()
    }
}