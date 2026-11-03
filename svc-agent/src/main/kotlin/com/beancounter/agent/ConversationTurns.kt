package com.beancounter.agent

import com.beancounter.agent.conversation.ConversationRecorder
import com.beancounter.agent.conversation.ConversationService
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.agent.conversation.Question
import com.beancounter.agent.conversation.TurnRecorder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Opens a turn in its conversation and records how it ended. */
@Component
class ConversationTurns(
    private val conversations: ConversationService,
    private val titler: ConversationTitler,
    private val pageQuerySelector: PageQuerySelector
) {
    private val log = LoggerFactory.getLogger(ConversationTurns::class.java)

    /**
     * Resolve the request's effective history and where its turn is recorded.
     * A request naming a conversation replays that conversation's stored turns
     * — client-sent history is ignored, so a caller cannot put words in the
     * assistant's mouth — and records this question to it now. Any other
     * request is stateless. The conversation was resolved by [TurnPlanner] on
     * the request thread, where the owner lookup has the caller's token and a
     * conversation the caller doesn't own surfaces as a 404.
     */
    fun open(
        request: AgentQuery,
        thread: ConversationThread?
    ): Pair<AgentQuery, TurnRecorder> {
        if (thread == null) return request to TurnRecorder.NONE
        val opened =
            conversations.openTurn(
                thread.ownerId,
                thread.id,
                Question(request.query, request.deepThink, request.label),
                pageQuerySelector.kindFor(request.context),
                TurnPrompts.MAX_HISTORY_TURNS
            )
        val recorder = ConversationRecorder(conversations, titler, thread.ownerId, thread.id, opened.titleSeed)
        return request.copy(history = opened.history) to recorder
    }

    /**
     * Record a blocking-path turn. Like [record], a failure to save is logged,
     * never allowed to cost the user the answer.
     */
    fun recordQuietly(action: () -> Unit) {
        try {
            action()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Failed to record conversation turn: {}", e.message, e)
        }
    }

    /**
     * Record off the Reactor event loop (JPA blocks). A failure to save is
     * logged, never allowed to cost the user the answer they are reading.
     */
    fun record(action: () -> Unit): Mono<Void> =
        Mono
            .fromRunnable<Void>(action)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError { e -> log.error("Failed to record conversation turn: {}", e.message, e) }
            .onErrorComplete()
}