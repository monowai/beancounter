package com.beancounter.agent

import org.springframework.stereotype.Service

/**
 * Maps a page context to a model id by its [PageDomain], the same mapping
 * [SystemPromptSelector] uses: one tier per analytical domain so we don't pay
 * SMART rates for chat-style holding lookups the FAST tier already answers well.
 *
 * Default is FAST. [PageDomain.smart] domains escalate to the SMART tier.
 *
 * Tested against [ChatModelSelectorTest] — the table there is the contract.
 */
@Service
class ChatModelSelector(
    private val tiers: AgentModelTiers
) {
    fun selectFor(
        context: Map<String, Any>?,
        deepThink: Boolean = false
    ): String {
        if (deepThink) return tiers.deep
        return if (isSmart(context)) tiers.smart else tiers.fast
    }

    /**
     * Whether a pre-canned prompt reasons before answering: the SMART pages do, at low effort,
     * because their briefs lean on tool figures and source labels that a short think keeps
     * honest. Everything else answers straight away for the lowest latency.
     */
    fun reasonsFor(context: Map<String, Any>?): Boolean = isSmart(context)

    private fun isSmart(context: Map<String, Any>?): Boolean = PageDomain.of(context).smart
}