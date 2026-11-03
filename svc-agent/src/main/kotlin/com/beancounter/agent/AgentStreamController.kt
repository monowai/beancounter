package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.InFlightTurns
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

/** The streaming transport of the Beancounter agent: `/agent/query` as Server-Sent Events. */
@RestController
@RequestMapping("/agent")
@Tag(name = "Agent", description = "Natural-language Beancounter assistant")
class AgentStreamController(
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val admission: TurnAdmission,
    private val streams: AnswerStreams,
    private val turns: ConversationTurns,
    private val inFlightTurns: InFlightTurns
) {
    /**
     * Streaming variant of [AgentController.query]. Returns a Server-Sent Events stream so
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
     *
     * A turn in a conversation outlives its subscriber. The question is
     * already in history by the time the stream starts ([ConversationTurns.open]), and the
     * answer is written only when the pipeline ends — so a client that drops
     * mid-answer (a phone that sleeps during a 40s Independence turn) would
     * otherwise leave an orphan question: the MVC emitter cancels a cold Flux,
     * the LLM call aborts, and cancellation reaches neither `onErrorResume`
     * nor the end-of-stream recording. So for a conversation the pipeline is
     * made hot and connected on the request thread, which the subscriber's
     * cancel cannot disconnect; [InFlightTurns] marks the conversation as
     * being answered — set before the question is written, so no reader ever
     * sees it unanswered and unclaimed — until the pipeline itself ends. A
     * turn refused by [ConversationTurns.open] clears the mark again. A stateless request has
     * no history to complete and stays cold: dropping it stops the LLM call.
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
        @RequestBody body: AgentQuery
    ): Flux<ServerSentEvent<String>> {
        scopeAuthorizer.authorize()
        return when (val admission = admission.admit(body)) {
            // A code, not prose: the streaming path's error payload is a contract
            // the UI renders copy from, and `/query` already answers this case
            // with the same NO_LLM code.
            is Admission.NoLlm -> streams.errorEvent(TurnFailure.NO_LLM)
            is Admission.Refused -> streams.errorEvent(admission.code)
            is Admission.Admitted -> turnStream(admission)
        }
    }

    /**
     * The pipeline for a turn [stream] has let run: cold for a stateless
     * request, hot and connected here for a conversation (see [stream]).
     */
    private fun turnStream(admitted: Admission.Admitted): Flux<ServerSentEvent<String>> {
        val plan = admitted.plan
        val conversationId = plan.thread?.id
        // Pending before the question is committed: ConversationTurns.open writes the user
        // row in its own transaction, and a GET landing between that commit
        // and this mark would show an unanswered question nobody is answering.
        // A refused turn (not owned, owner lookup failed) leaves no residue.
        conversationId?.let(inFlightTurns::begin)
        val pipeline =
            try {
                val subject = plan.opening
                if (subject == null) {
                    val (turn, recorder) = turns.open(plan.request, plan.thread)
                    streams.pipelineFor(admitted, turn, recorder)
                } else {
                    streams.openingPipeline(plan.request, subject, plan.thread, admitted.meter)
                }
            } catch (e: Exception) {
                conversationId?.let(inFlightTurns::end)
                throw e
            }
        // doFinally sits inside the hot boundary, so it fires when the
        // pipeline itself completes or fails — after the record{} write that
        // ends it — and never on the subscriber's cancel. replay() keeps the
        // tokens emitted before the MVC emitter subscribes (it attaches after
        // this method returns); autoConnect(0) subscribes right here, on the
        // request thread, so contextCapture still snapshots the caller's
        // SecurityContext, and unlike refCount it never disconnects when the
        // downstream count drops to zero.
        return if (conversationId == null) {
            pipeline
        } else {
            pipeline
                .doFinally { inFlightTurns.end(conversationId) }
                .replay()
                .autoConnect(0)
        }
    }
}