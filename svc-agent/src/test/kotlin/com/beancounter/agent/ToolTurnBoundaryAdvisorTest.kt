package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tool.annotation.Tool
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

private const val TOOL_NAME = "fetchBriefingData"

/**
 * A real Spring AI tool: the resolved name defaults to the method name, so this
 * must match [TOOL_NAME] for [org.springframework.ai.model.tool.ToolCallingManager]
 * to find and invoke it against the [ToolCallChatModel]'s scripted [AssistantMessage.ToolCall].
 */
private class BriefingTool {
    @Tool(description = "Fetches the data needed for the test briefing")
    fun fetchBriefingData(): String = "briefing-data"
}

/** A text-only chunk, as a provider emits per token batch. */
private fun textChunk(
    text: String,
    finishReason: String
): ChatResponse =
    ChatResponse(
        listOf(
            Generation(
                AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
            )
        )
    )

/**
 * The chunk that actually carries the tool call — this is the element
 * [org.springframework.ai.chat.client.advisor.ToolCallingAdvisor]'s outer filter
 * removes before it reaches a `ChatClient…stream().chatResponse()` consumer.
 */
private fun toolCallChunk(): ChatResponse =
    ChatResponse(
        listOf(
            Generation(
                AssistantMessage
                    .builder()
                    .content("")
                    .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", TOOL_NAME, "{}")))
                    .build(),
                ChatGenerationMetadata.builder().finishReason("TOOL_CALLS").build()
            )
        )
    )

/**
 * A fake [ChatModel] whose `stream(Prompt)` replays a scripted sequence of
 * [ChatResponse] fluxes, one per tool-calling turn — call 1 for the first turn,
 * call 2 for the recursive turn [org.springframework.ai.chat.client.advisor.ToolCallingAdvisor]
 * makes after executing the tool call. [getOptions] returns
 * [ToolCallingChatOptions] because [org.springframework.ai.chat.client.DefaultChatClientUtils]
 * only threads `.tools(...)` onto the prompt — and `ToolCallingAdvisor` only engages
 * its loop at all — when `chatModel.getOptions().mutate()` is already a
 * `ToolCallingChatOptions.Builder`.
 */
private class ToolCallChatModel(
    private val turns: List<() -> Flux<ChatResponse>>
) : ChatModel {
    val invocationCount = AtomicInteger(0)

    override fun call(prompt: Prompt): ChatResponse = throw UnsupportedOperationException("blocking call not used")

    override fun getOptions(): ChatOptions = ToolCallingChatOptions.builder().build()

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        val turn = invocationCount.getAndIncrement()
        return turns[turn.coerceAtMost(turns.size - 1)]()
    }
}

/**
 * Integration-style tests through a REAL [ChatClient] — the shape PR #1112's tests
 * lacked. A mock-based test can hand the controller a `ChatResponse` carrying a
 * finish reason directly; production never gets that chance, because
 * `ToolCallingAdvisor`'s own filter strips every tool-call-carrying element before
 * it reaches `ChatClient…stream().chatResponse()`. Only a real advisor chain
 * proves [ToolTurnBoundaryAdvisor]'s synthetic element survives that filter.
 */
class ToolTurnBoundaryAdvisorTest {
    @Test
    fun `should append a filter-surviving boundary element after a tool-calling turn`() {
        val chatModel =
            ToolCallChatModel(
                listOf(
                    // Turn 1: DeepSeek-style narration (finishReason "" on every
                    // chunk until the terminal one), then the chunk carrying the
                    // tool call.
                    { Flux.just(textChunk("I'll gather the data needed for this briefing…", ""), toolCallChunk()) },
                    // Turn 2: the final answer, after the tool executed.
                    { Flux.just(textChunk("Final answer.", "STOP")) }
                )
            )
        val client = ChatClient.builder(chatModel).defaultAdvisors(ToolTurnBoundaryAdvisor()).build()

        val responses =
            client
                .prompt()
                .user("brief me")
                .tools(BriefingTool())
                .stream()
                .chatResponse()
                .collectList()
                .block(Duration.ofSeconds(5))!!

        assertThat(responses.map { it.result?.output?.text })
            .containsExactly(
                "I'll gather the data needed for this briefing…",
                "",
                "Final answer."
            )

        val boundary = responses[1]
        assertThat(
            boundary.result
                ?.metadata
                ?.finishReason
                ?.lowercase()
        ).isEqualTo("tool_calls")
        assertThat(boundary.hasToolCalls()).isFalse()

        // The filter contract this advisor depends on: ToolCallingAdvisor strips
        // every element that DOES carry tool calls, so none should ever reach
        // the client — if this fails, the advisor's own premise is wrong.
        assertThat(responses).noneMatch(ChatResponse::hasToolCalls)

        // Proves the tool actually executed (two turns), not just that the
        // scripted flux happened to contain two elements.
        assertThat(chatModel.invocationCount.get()).isEqualTo(2)
    }

    @Test
    fun `should not append a boundary element when the turn does not call tools`() {
        val chatModel = ToolCallChatModel(listOf({ Flux.just(textChunk("Final answer.", "STOP")) }))
        val client = ChatClient.builder(chatModel).defaultAdvisors(ToolTurnBoundaryAdvisor()).build()

        val responses =
            client
                .prompt()
                .user("brief me")
                .tools(BriefingTool())
                .stream()
                .chatResponse()
                .collectList()
                .block(Duration.ofSeconds(5))!!

        assertThat(responses).hasSize(1)
        assertThat(responses[0].result?.output?.text).isEqualTo("Final answer.")
        assertThat(chatModel.invocationCount.get()).isEqualTo(1)
    }
}