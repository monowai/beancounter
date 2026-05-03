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
        val page = context?.get("page")?.toString()?.lowercase() ?: ""
        val isSmart = SMART_DOMAINS.any { it in page }
        return if (isSmart) tiers.smart else tiers.fast
    }

    companion object {
        // Substrings checked against `page.lowercase()`. Keep narrow:
        // anything not listed defaults to FAST.
        private val SMART_DOMAINS =
            listOf(
                "asset review",
                "asset-review",
                "independence",
                "retire",
                "rebalanc"
            )
    }
}