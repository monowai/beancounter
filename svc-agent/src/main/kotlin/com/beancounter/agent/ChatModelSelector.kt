package com.beancounter.agent

import org.springframework.stereotype.Service

/**
 * Maps a page context to an Anthropic model id. Mirrors [SystemPromptSelector]:
 * one tier per analytical domain so we don't pay Sonnet/Opus rates for chat-style
 * holding lookups that Haiku already answers well.
 *
 * Default is FAST. Domains in [SMART_DOMAINS] escalate to the SMART tier.
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

    private fun isSmart(context: Map<String, Any>?): Boolean {
        val page = context?.get("page")?.toString()?.lowercase() ?: ""
        return SMART_DOMAINS.any { it in page }
    }

    companion object {
        // Substrings checked against `page.lowercase()`. Keep narrow:
        // anything not listed defaults to FAST.
        private val SMART_DOMAINS =
            listOf(
                "asset review",
                "asset-review",
                "holding analysis",
                "holding-analysis",
                "independence",
                "retire",
                "rebalanc",
                "watchlist"
            )
    }
}