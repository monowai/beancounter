package com.beancounter.agent

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

private const val EVENT_TOKEN = "token"
private const val EVENT_RESET = "reset"

/** The SSE events each streamed chunk becomes. */
class StreamTallyTest {
    @Test
    fun `should emit token event for narration text with no finish reason`() {
        val events = StreamTally.eventsFor("Hello", null)

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Hello")
    }

    @Test
    fun `should append reset event when turn finishes with tool calls`() {
        val events = StreamTally.eventsFor("Let me gather that data...", "TOOL_CALLS")

        assertThat(events).hasSize(2)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Let me gather that data...")
        assertThat(events[1].event()).isEqualTo(EVENT_RESET)
    }

    @Test
    fun `should append reset event when turn finishes with anthropic tool use`() {
        // Anthropic (svc-agent's default provider — model.chat: anthropic) sets
        // finishReason from StopReason.toString(), whose wire value for a
        // tool-calling turn is "tool_use", not "tool_calls". Without matching
        // this, the preamble bug survives on the default routing path.
        val events = StreamTally.eventsFor("Let me check that...", "tool_use")

        assertThat(events).hasSize(2)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Let me check that...")
        assertThat(events[1].event()).isEqualTo(EVENT_RESET)
    }

    @Test
    fun `should not emit reset and keep streaming for unknown finish reason`() {
        // A provider/Spring AI version bump could change the tool-turn wire
        // value without this code noticing — the safe default is to keep
        // streaming and not fire a false reset. StreamTally's log.warn
        // covers the observability side (not asserted here — see PR review).
        val events = StreamTally.eventsFor("Some narration.", "banana")

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Some narration.")
    }

    @Test
    fun `should emit only reset for empty metadata-only tool-call chunk`() {
        // Case-insensitive: DeepSeek emits "TOOL_CALLS" but the check must also
        // match lowercase, e.g. an OpenAI-style finish reason.
        val events = StreamTally.eventsFor("", "tool_calls")

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_RESET)
    }

    @Test
    fun `should not emit reset when turn finishes with stop`() {
        val events = StreamTally.eventsFor("Final answer.", "STOP")

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Final answer.")
    }

    @Test
    fun `should return empty list and not warn for blank text and blank finish reason`() {
        // DeepSeekChatModel maps an absent finish_reason to "" on EVERY streamed
        // chunk (not once per turn) — a blank reason must be treated the same as
        // null, or this warns dozens of times a second in production.
        lateinit var events: List<org.springframework.http.codec.ServerSentEvent<String>>
        val warnings = captureWarnings { events = StreamTally.eventsFor("", "") }

        assertThat(events).isEmpty()
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `should emit exactly one token event for text with a blank finish reason`() {
        val events = StreamTally.eventsFor("hi", "")

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("hi")
    }

    /** Mirrors AlphaClassificationEnricherTest's captureWarnings pattern. */
    private fun captureWarnings(block: () -> Unit): List<String> {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(StreamTally::class.java) as Logger
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }
}