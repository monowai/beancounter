package com.beancounter.agent.holding

import com.beancounter.agent.AgentQuery
import com.beancounter.agent.HoldingAnalysisSubject
import com.beancounter.agent.PageQueries
import com.beancounter.agent.ReviewTurn
import com.beancounter.agent.TurnFailure
import com.beancounter.agent.TurnPlanner
import com.beancounter.agent.TurnPrompts
import com.beancounter.agent.UnansweredTurn
import com.beancounter.agent.review.AssetReviewCache
import com.beancounter.agent.review.ReviewCachingRecorder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** A Holding Analysis's opening turn, ready to ask, or the stable code it failed with. */
sealed interface Opening {
    data class Ready(
        val turn: AgentQuery
    ) : Opening

    data class Failed(
        val code: String
    ) : Opening
}

/** Builds a Holding Analysis's opening question from the caller's holding and the asset's review. */
@Component
class HoldingAnalysisOpening(
    private val holdings: HoldingFactsSource,
    private val planner: TurnPlanner,
    private val prompts: TurnPrompts,
    private val reviewCache: AssetReviewCache
) {
    private val log = LoggerFactory.getLogger(HoldingAnalysisOpening::class.java)

    /**
     * The opening turn of a Holding Analysis. The caller's holding is read
     * first, server side with their own credentials, so a holding they do not
     * have costs no model call; then the asset's review, cached or generated
     * ([answer]). The question carries both: the review labelled with its
     * date, then the holding's figures, then [PageQueries.holdingAnalysis]'s
     * ask. Never cached: it is the caller's own.
     */
    fun open(
        request: AgentQuery,
        subject: HoldingAnalysisSubject
    ): Opening =
        try {
            val facts = holdings.find(subject)
            if (facts == null) {
                Opening.Failed(TurnFailure.NOT_HELD)
            } else {
                val review = planner.reviewTurn(subject.asset, request)
                Opening.Ready(
                    request.copy(
                        query =
                            PageQueries.holdingAnalysis(
                                subject,
                                review.key.reviewDate,
                                answer(review, request.refresh),
                                facts
                            ),
                        label = request.label ?: PageQueries.holdingAnalysisLabel(subject.ticker)
                    )
                )
            }
        } catch (
            // Before any stream exists: classified like a stream failure, never a 500.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            val code = TurnFailure.classify(e)
            log.error("Holding analysis could not open ({}): {}", code, e.message, e)
            Opening.Failed(code)
        }

    /**
     * The answer to [review]: the cached one when fresh (unless the caller
     * asked to [AgentQuery.refresh] it), else asked of the model and stored
     * for every caller, as [ReviewCachingRecorder] would.
     */
    private fun answer(
        review: ReviewTurn,
        refresh: Boolean
    ): String {
        val hit = planner.cachedReview(review.key, refresh)
        if (hit != null) return hit
        val answer = prompts.ask(review.turn)
        if (answer.content.isBlank()) throw UnansweredTurn(TurnFailure.emptyAnswerCode(answer.finishReason))
        reviewCache.store(review.key, answer.content)
        return answer.content
    }
}