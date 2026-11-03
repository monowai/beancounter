package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.TurnRecorder
import com.beancounter.agent.quota.QuotaRecorder
import com.beancounter.agent.quota.TurnQuota
import org.springframework.stereotype.Component

/** Counts live turns against the caller's weekly AI quota, and refuses those it cannot run. */
@Component
class TurnMetering(
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val conversationOwner: ConversationOwner,
    private val quota: TurnQuota
) {
    /**
     * Who [body]'s turn is counted against: the caller's `SystemUser.id` when
     * metered ([AgentScopeAuthorizer.metered]), else null. Read on the request
     * thread, where the owner lookup has the caller's token.
     */
    fun meterFor(
        body: AgentQuery,
        plan: Plan
    ): String? = if (scopeAuthorizer.metered(body)) plan.thread?.ownerId ?: conversationOwner.id() else null

    /**
     * Why a live turn is refused, or null to run it: an unconfirmed email
     * address first, then a metered caller an administrator has disabled, then
     * one with no turn left this week. A free one-off News lookup is never
     * metered, so it needs no caller identity and is not refused for either.
     * Asked only on an Asset Review cache miss: a hit is served free to anyone.
     */
    fun refusalFor(meter: String?): String? =
        when {
            !scopeAuthorizer.verified() -> TurnFailure.EMAIL_UNVERIFIED
            meter == null -> null
            quota.disabled(meter) -> TurnFailure.AI_DISABLED
            !quota.allows(meter) -> TurnFailure.AI_QUOTA
            else -> null
        }

    /** [recorder], counting an answered turn against [meter] when metered. */
    fun metered(
        recorder: TurnRecorder,
        meter: String?
    ): TurnRecorder = meter?.let { QuotaRecorder(recorder, quota, it) } ?: recorder
}