package com.beancounter.agent

import com.beancounter.agent.conversation.ConversationKind
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationService
import com.beancounter.agent.review.AssetReviewCache
import com.beancounter.agent.review.AssetReviewKey
import com.beancounter.agent.tools.ToolSelector
import com.beancounter.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/** The caller's conversation a request names: its owner, and what it already is. */
data class ConversationThread(
    val ownerId: String,
    val id: String,
    val kind: ConversationKind,
    val hasTurns: Boolean
)

/**
 * How one request is answered: [request] is the turn to ask, or, when
 * [opening] is set, the start of a Holding Analysis whose question is
 * built from that subject when the turn opens.
 */
data class Plan(
    val request: AgentQuery,
    val opening: HoldingAnalysisSubject?,
    val thread: ConversationThread?
) {
    /** What the turn is stored and echoed under: the caller's label, else a Holding Analysis's subject. */
    val label: String
        get() = request.label ?: opening?.let { PageQueries.holdingAnalysisLabel(it.ticker) } ?: request.query
}

/** A canned Asset Review asked from the asset alone, and where its answer is cached. */
data class ReviewTurn(
    val turn: AgentQuery,
    val key: AssetReviewKey
)

/**
 * Decides what a request asks: the question (the caller's own or the page's
 * canned one), the conversation it belongs to, whether it opens a Holding
 * Analysis, and where its canned Asset Review is cached.
 */
@Component
class TurnPlanner(
    private val pageQuerySelector: PageQuerySelector,
    private val conversations: ConversationService,
    private val conversationOwner: ConversationOwner,
    private val reviewCache: AssetReviewCache,
    private val routing: ModelRouting,
    // Dates a review's cache key. Defaulted so Spring wires it without a Clock bean; fixed in tests.
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(TurnPlanner::class.java)

    private companion object {
        const val QUERY_REQUIRED = "A query is required for this page"
    }

    /** The conversation [body] names, resolved for the caller, or null for a stateless request. */
    private fun threadOf(body: AgentQuery): ConversationThread? {
        val id = body.conversationId ?: return null
        val ownerId = conversationOwner.id()
        val state = conversations.stateOf(ownerId, id)
        return ConversationThread(ownerId, id, state.kind, state.hasTurns)
    }

    /**
     * Plan [body]. A Holding Analysis is routed by its conversation as much
     * as by its page: a turn in a Holding Analysis conversation that has begun
     * is a follow-up, answered with the Holding Analysis prompt and tools
     * whatever page it names, and asks the caller's own question; only a blank
     * question that starts a conversation (or a stateless request) opens one.
     * Its context is the subject's canonical one, so nothing else a caller
     * sent (figures included) reaches the prompt. Refused with a 400: a
     * malformed subject ([PageQuerySelector.INVALID_HOLDING_ANALYSIS]), one
     * asked inside an Asset Review conversation — about the asset alone —
     * ([PageQuerySelector.HOLDING_IN_ASSET_REVIEW]), and a blank question that
     * would open nothing. Any other page is planned as [withQuestion] and
     * [canonical] make it.
     */
    fun plan(body: AgentQuery): Plan {
        val thread = threadOf(body)
        val subject = pageQuerySelector.holdingAnalysisSubject(body.context)
        if (subject != null && thread?.kind == ConversationKind.ASSET_REVIEW) {
            throw BusinessException(PageQuerySelector.HOLDING_IN_ASSET_REVIEW)
        }
        val followUp = thread?.kind == ConversationKind.HOLDING_ANALYSIS && thread.hasTurns
        if (subject == null && !followUp) return Plan(withQuestion(canonical(body)), null, thread)
        val context =
            subject?.let(pageQuerySelector::holdingAnalysisContext)
                ?: mapOf("page" to PageQueries.HOLDING_ANALYSIS)
        val opens = body.query.isBlank() && body.history.isNullOrEmpty() && thread?.hasTurns != true
        if (body.query.isBlank() && !opens) throw BusinessException(QUERY_REQUIRED)
        return Plan(body.copy(context = context), if (opens) subject else null, thread)
    }

    /**
     * The question this turn asks: the caller's own when it sent one, else the
     * page's canned question ([PageQuerySelector]), stored under the page's name
     * when the caller gave no label. A page with no canned question needs the
     * caller to ask something — a 400 otherwise.
     */
    private fun withQuestion(request: AgentQuery): AgentQuery {
        if (request.query.isNotBlank()) return request
        val canned =
            pageQuerySelector.queryFor(request.context)
                ?: throw BusinessException(QUERY_REQUIRED)
        return request.copy(query = canned.question, label = request.label ?: canned.label)
    }

    /**
     * A canned Asset Review that may be shared, rebuilt from its cache key
     * alone: the page, ticker and market, with the server's label. A shared
     * answer reaches every user, so caller-supplied context (`assetName`, any
     * other key) and label must never shape its prompt. Anything else is
     * returned as sent.
     */
    private fun canonical(body: AgentQuery): AgentQuery {
        if (!reviewCache.enabled || body.query.isNotBlank() || !body.history.isNullOrEmpty()) return body
        val subject = pageQuerySelector.assetReviewSubject(body.context) ?: return body
        return body.copy(context = pageQuerySelector.assetReviewContext(subject), label = null)
    }

    /**
     * Where this turn's canned Asset Review is cached, or null when it is not
     * one: the caller asked nothing of its own and neither the request nor the
     * conversation carries earlier turns, and it opens no Holding Analysis. Read before the turn is recorded, so
     * a cache hit is known before the quota is asked. A free-form question or a follow-up
     * is always answered live. The review reads no user data (see
     * [ToolSelector]), so it is keyed by the asset alone, the date stamped
     * into the prompt, and the model that answers — never by the caller.
     */
    fun reviewKeyFor(
        body: AgentQuery,
        plan: Plan
    ): AssetReviewKey? {
        if (!reviewCache.enabled || plan.opening != null) return null
        if (body.query.isNotBlank() || !body.history.isNullOrEmpty() || plan.thread?.hasTurns == true) return null
        val turn = plan.request
        val subject = pageQuerySelector.assetReviewSubject(turn.context) ?: return null
        return AssetReviewKey(
            market = subject.market,
            ticker = subject.ticker,
            reviewDate = LocalDate.now(clock),
            model = routing.answeringModel(turn)
        )
    }

    /**
     * The cached answer for [key], or null on a miss or when the caller asked
     * to [AgentQuery.refresh] it. A failed read is a miss ([AssetReviewCache.find]).
     */
    fun cachedReview(
        key: AssetReviewKey,
        refresh: Boolean
    ): String? {
        val hit = if (refresh) null else reviewCache.find(key)
        log.debug(
            "Asset review cache {} for {}:{} on {}",
            if (refresh) {
                "refresh"
            } else if (hit == null) {
                "miss"
            } else {
                "hit"
            },
            key.market,
            key.ticker,
            key.reviewDate
        )
        return hit
    }

    /**
     * The Asset Review of [asset] as the canonical cached review is asked —
     * from the asset alone, never the caller's context — dated today and
     * keyed by the model that answers. [request] lends only its thinking flags.
     */
    fun reviewTurn(
        asset: AssetReviewSubject,
        request: AgentQuery
    ): ReviewTurn {
        val turn =
            withQuestion(
                AgentQuery(
                    context = pageQuerySelector.assetReviewContext(asset),
                    think = request.think,
                    deepThink = request.deepThink
                )
            )
        return ReviewTurn(
            turn,
            AssetReviewKey(asset.market, asset.ticker, LocalDate.now(clock), routing.answeringModel(turn))
        )
    }
}