package com.beancounter.agent

import com.beancounter.agent.review.AssetReviewKey
import org.springframework.stereotype.Component

/** Whether a request may run, decided once before anything is recorded. */
sealed interface Admission {
    /** No LLM is configured; [request] is the planned turn, for the reply. */
    data class NoLlm(
        val request: AgentQuery
    ) : Admission

    /** Refused with the stable [code]; [label] is what the caller asked about. */
    data class Refused(
        val code: String,
        val label: String
    ) : Admission

    /**
     * The turn may run: answered from the cached review [hit] stored under
     * [reviewKey], or live and counted against [meter] when metered.
     */
    data class Admitted(
        val plan: Plan,
        val reviewKey: AssetReviewKey?,
        val hit: String?,
        val meter: String?
    ) : Admission
}

/** Plans a request and decides, once, whether it may run. */
@Component
class TurnAdmission(
    private val planner: TurnPlanner,
    private val metering: TurnMetering,
    private val routing: ModelRouting
) {
    /**
     * Admit [body]. A bad request (see [TurnPlanner.plan]) throws. A cached
     * Asset Review is looked up before the quota is asked, so a hit is
     * served free to anyone; a refused turn is refused before its question is
     * recorded, so an exhausted caller leaves no residue.
     */
    fun admit(body: AgentQuery): Admission {
        val plan = planner.plan(body)
        if (!routing.available) return Admission.NoLlm(plan.request)
        val reviewKey = planner.reviewKeyFor(body, plan)
        val hit = reviewKey?.let { planner.cachedReview(it, body.refresh) }
        val meter = metering.meterFor(body, plan)
        val refused = if (hit == null) metering.refusalFor(meter) else null
        return if (refused !=
            null
        ) {
            Admission.Refused(refused, plan.label)
        } else {
            Admission.Admitted(plan, reviewKey, hit, meter)
        }
    }
}