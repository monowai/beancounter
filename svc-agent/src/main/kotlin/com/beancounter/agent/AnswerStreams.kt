package com.beancounter.agent

import com.beancounter.agent.conversation.TurnRecorder
import com.beancounter.agent.holding.HoldingAnalysisOpening
import com.beancounter.agent.holding.Opening
import com.beancounter.agent.review.AssetReviewKey
import org.slf4j.LoggerFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Answers an admitted turn as a Server-Sent Events stream: `token` events
 * as the model writes, a `reset` after narration that preceded tool calls,
 * then `done` — or a terminal `error` carrying a stable [TurnFailure] code.
 */
@Component
class AnswerStreams(
    private val prompts: TurnPrompts,
    private val routing: ModelRouting,
    private val llmMetrics: LlmMetrics,
    private val objectMapper: ObjectMapper,
    private val turns: ConversationTurns,
    private val opening: HoldingAnalysisOpening
) {
    private val log = LoggerFactory.getLogger(AnswerStreams::class.java)

    private companion object {
        // Two retries with exponential backoff (+jitter) cover the common
        // sub-second connectivity blip without holding the request open long
        // enough to outlast a real outage — that surfaces as the error envelope.
        const val STREAM_RETRY_ATTEMPTS = 2L
        val STREAM_RETRY_MIN_BACKOFF: Duration = Duration.ofMillis(250)

        // Leading chars of the user's question kept in a log line — enough to
        // identify the request in kauri's logs without shipping the whole
        // question (which can carry page context the user typed).
        const val QUERY_LOG_CHARS = 120
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
    fun openingPipeline(
        request: AgentQuery,
        subject: HoldingAnalysisSubject,
        thread: ConversationThread?,
        meter: String?
    ): Flux<ServerSentEvent<String>> =
        Mono
            .just(subject)
            .publishOn(Schedulers.boundedElastic())
            .flatMapMany { holding ->
                when (val opening = opening.open(request, holding)) {
                    is Opening.Failed -> {
                        errorEvent(opening.code)
                    }
                    is Opening.Ready -> {
                        val (turn, recorder) = turns.open(opening.turn, thread)
                        answerPipeline(turn, turns.live(recorder, null, meter))
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
    fun pipelineFor(
        admitted: Admission.Admitted,
        turn: AgentQuery,
        recorder: TurnRecorder
    ): Flux<ServerSentEvent<String>> {
        val key = admitted.reviewKey
        val hit = admitted.hit
        if (key != null && hit != null) return cachedPipeline(hit, key, recorder)
        return answerPipeline(turn, turns.live(recorder, key, admitted.meter))
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
            .just(ServerSentEvent.builder(content).event(SseEvent.TOKEN).build())
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
        return ServerSentEvent.builder(payload).event(SseEvent.DONE).build()
    }

    fun errorEvent(message: String): Flux<ServerSentEvent<String>> =
        Flux.just(ServerSentEvent.builder<String>(message).event(SseEvent.ERROR).build())
}