package com.beancounter.agent

import com.beancounter.agent.conversation.TurnRecorder
import com.beancounter.agent.holding.HoldingAnalysisOpening
import com.beancounter.agent.holding.Opening
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils
import java.time.Instant

/** Answers an admitted turn in one blocking `/query` response, or the failure it ended in. */
@Component
class BlockingAnswers(
    private val prompts: TurnPrompts,
    private val llmMetrics: LlmMetrics,
    private val turns: ConversationTurns,
    private val opening: HoldingAnalysisOpening
) {
    private val log = LoggerFactory.getLogger(BlockingAnswers::class.java)

    private companion object {
        // Escapes markup only: a label's dash is not an entity.
        const val UTF_8 = "UTF-8"

        // Leading chars of the user's question kept in a log line.
        const val QUERY_LOG_CHARS = 120
    }

    /** A failed `/query`, in the shape every failure takes. */
    fun failure(
        safeQuery: String,
        code: String
    ): ResponseEntity<AgentResponse> =
        ResponseEntity
            .status(TurnFailure.statusFor(code))
            .body(
                AgentResponse(
                    query = safeQuery,
                    response = TurnFailure.messageFor(code),
                    timestamp = Instant.now().toString(),
                    error = code
                )
            )

    /** The `/query` answer when no LLM is configured. */
    fun noLlm(request: AgentQuery): ResponseEntity<AgentResponse> =
        ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                AgentResponse(
                    query = HtmlUtils.htmlEscape(request.query),
                    response =
                        "No LLM is configured. Set ${AgentProvider.PROPERTY} to '${AgentProvider.DEEPSEEK}', " +
                            "'${AgentProvider.ANTHROPIC}', '${AgentProvider.OPENAI}', or '${AgentProvider.OLLAMA}'.",
                    timestamp = Instant.now().toString(),
                    error = TurnFailure.NO_LLM
                )
            )

    /** A `/query` turn the caller may run: its opening built first, when it has one. */
    fun answer(admitted: Admission.Admitted): ResponseEntity<AgentResponse> {
        val plan = admitted.plan
        val request = plan.request
        return when (val opening = plan.opening?.let { opening.open(request, it) } ?: Opening.Ready(request)) {
            // Echo what the caller asked about: the opening question was never built.
            is Opening.Failed -> {
                failure(HtmlUtils.htmlEscape(plan.label, UTF_8), opening.code)
            }
            is Opening.Ready -> {
                answerTurn(opening.turn, admitted)
            }
        }
    }

    /** [asked], recorded in its conversation, answered from the cached review or live. */
    private fun answerTurn(
        asked: AgentQuery,
        admitted: Admission.Admitted
    ): ResponseEntity<AgentResponse> {
        val reviewKey = admitted.reviewKey
        val hit = admitted.hit
        val meter = admitted.meter
        val safeQuery = HtmlUtils.htmlEscape(asked.query)
        val (turn, conversationRecorder) = turns.open(asked, admitted.plan.thread)
        if (reviewKey != null && hit != null) {
            // No model runs: the stored answer is recorded as a live one would be, and not counted.
            llmMetrics.cached(LlmMetrics.Mode.CALL, reviewKey.model)
            turns.recordQuietly { conversationRecorder.answered(hit) }
            return ResponseEntity.ok(
                AgentResponse(
                    query = safeQuery,
                    response = hit,
                    timestamp = Instant.now().toString(),
                    cached = true
                )
            )
        }
        return askLive(turn, safeQuery, turns.live(conversationRecorder, reviewKey, meter))
    }

    /** [turn] asked of the model, its answer or failure recorded and returned. */
    private fun askLive(
        turn: AgentQuery,
        safeQuery: String,
        recorder: TurnRecorder
    ): ResponseEntity<AgentResponse> =
        try {
            answered(prompts.ask(turn), safeQuery, recorder)
        } catch (
            // Controller boundary: every LLM/tool failure becomes an error
            // body, classified the same way the streaming path classifies it.
            // Without this the two transports disagreed — a stream told the UI
            // "provider-quota", the same failure on /query told it
            // "agent-error", and the non-streaming surfaces (Asset Review, News
            // Sentiment) could only ever say something went wrong.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            val errorCode = TurnFailure.classify(e)
            log.error("Agent query failed ({}): {}", errorCode, e.message, e)
            turns.recordQuietly { recorder.failed(errorCode) }
            failure(safeQuery, errorCode)
        }

    /** The model's [answer] to a `/query` turn, recorded; a blank one is a failure. */
    private fun answered(
        answer: ModelAnswer,
        safeQuery: String,
        recorder: TurnRecorder
    ): ResponseEntity<AgentResponse> {
        val content = answer.content

        // Same hole the streaming path had: a turn can succeed at the HTTP
        // level and still say nothing (typically after the context window
        // filled with tool results). Answering with a blank body left every
        // caller rendering an empty response and no reason for it.
        if (content.isBlank()) {
            val code = TurnFailure.emptyAnswerCode(answer.finishReason)
            log.warn(
                "Agent query produced no answer: code={}, model={}, query=\"{}\"",
                code,
                answer.modelId,
                safeQuery.take(QUERY_LOG_CHARS)
            )
            turns.recordQuietly { recorder.failed(code) }
            return failure(safeQuery, code)
        }

        turns.recordQuietly { recorder.answered(content) }
        return ResponseEntity.ok(
            AgentResponse(
                query = safeQuery,
                response = content,
                timestamp = Instant.now().toString()
            )
        )
    }
}