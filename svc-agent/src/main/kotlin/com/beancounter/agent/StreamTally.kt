package com.beancounter.agent

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.http.codec.ServerSentEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * One streamed turn's running account, fed every `ChatResponse` chunk by
 * [accept], which answers with the SSE events that chunk becomes.
 */
class StreamTally {
    private val totalChars = AtomicLong(0)

    // The *answer* — narration before a tool turn doesn't count, since the
    // reader is left with nothing when the turn that follows it never
    // produces text. Cleared at every tool-turn boundary, which is why this
    // can't stand in for `totalChars` (that one guards connect-retry and
    // has to count every char ever streamed). It is also what a
    // conversation records as the assistant's reply.
    private val answerText = StringBuilder()

    // Last non-blank finish reason seen, for classifying an answerless
    // stream: `length`/`max_tokens` mean truncation, anything else means
    // the model simply said nothing.
    private val lastFinishReason = AtomicReference<String?>(null)

    // Spring AI's chatResponse() Flux surfaces ChatResponse per chunk; the
    // final emission carries usage tokens.
    private val lastUsage = AtomicReference<Usage?>(null)

    /** Every char streamed to the client, narration included. */
    val chars: Long
        get() = totalChars.get()

    /** The answer text, empty when the turn produced none. */
    val answer: String
        get() = answerText.toString()

    val finishReason: String?
        get() = lastFinishReason.get()

    val usage: Usage?
        get() = lastUsage.get()

    /**
     * Account for one chunk. Spring AI emits trailing chunks with no
     * Generation (metadata-only — token usage, finishReason), so the result
     * is read null-safely and the text may be empty.
     */
    fun accept(response: ChatResponse): List<ServerSentEvent<String>> {
        lastUsage.set(response.metadata.usage)
        val text =
            response.result
                ?.output
                ?.text
                .orEmpty()
        val finishReason = response.result?.metadata?.finishReason
        if (text.isNotEmpty()) {
            totalChars.addAndGet(text.length.toLong())
            answerText.append(text)
        }
        val reason = finishReason?.takeIf { it.isNotBlank() }?.lowercase()
        if (reason != null) {
            lastFinishReason.set(reason)
            // Whatever was said before the tool call was narration,
            // not the answer — the answer is what comes after.
            if (reason in TOOL_TURN_FINISH_REASONS) answerText.setLength(0)
        }
        return eventsFor(text, finishReason)
    }

    companion object {
        private val log = LoggerFactory.getLogger(StreamTally::class.java)

        // Finish reasons that mark a turn as narration-before-tool-calls,
        // lower-cased for case-insensitive matching in eventsFor. The
        // production source is ToolTurnBoundaryAdvisor's synthetic element
        // (via the shared BOUNDARY_FINISH_REASON constant); the provider wire
        // values ("tool_calls" DeepSeek/OpenAI, "tool_use" from Anthropic's
        // StopReason.toString()) stay listed in case a Spring AI change ever
        // lets a real one through. Do NOT add "pause_turn" or any other
        // Anthropic stop reason here.
        private val TOOL_TURN_FINISH_REASONS = setOf(ToolTurnBoundaryAdvisor.BOUNDARY_FINISH_REASON, "tool_use")

        // Every other finish reason a turn can legitimately end with — the
        // final answer. DeepSeek/OpenAI: "stop", "length". Anthropic:
        // "end_turn", "stop_sequence", "max_tokens", "pause_turn". Anything
        // outside both this set and TOOL_TURN_FINISH_REASONS is unrecognized
        // (e.g. a provider/Spring AI version bump changing the wire value)
        // and gets logged rather than silently dropped.
        private val KNOWN_FINAL_FINISH_REASONS =
            setOf("stop", "end_turn", "stop_sequence", "pause_turn") + TurnFailure.TRUNCATION_FINISH_REASONS

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
         * this tally, so the real element never gets here to be read.
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
        internal fun eventsFor(
            text: String,
            finishReason: String?
        ): List<ServerSentEvent<String>> =
            buildList {
                if (text.isNotEmpty()) add(ServerSentEvent.builder(text).event(SseEvent.TOKEN).build())
                val reason = finishReason?.takeIf { it.isNotBlank() }?.lowercase()
                when {
                    reason == null -> {
                        Unit
                    }
                    reason in TOOL_TURN_FINISH_REASONS -> {
                        add(ServerSentEvent.builder("").event(SseEvent.RESET).build())
                    }
                    reason !in KNOWN_FINAL_FINISH_REASONS -> {
                        log.warn(
                            "Unrecognized finish reason '{}' — reset event not emitted; check provider wire values",
                            finishReason
                        )
                    }
                }
            }
    }
}