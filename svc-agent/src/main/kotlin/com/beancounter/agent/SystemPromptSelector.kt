package com.beancounter.agent

import org.springframework.stereotype.Service

/**
 * Maps a page context's [PageDomain] to a focused domain system prompt. One
 * prompt per domain keeps each call tight (fewer input tokens) and lets the
 * Anthropic system-prompt cache key on a stable string per domain. An FX pair
 * on an Asset Review page gets the FX review instead.
 *
 * See [DomainSystemPrompts] for the prompt content.
 */
@Service
class SystemPromptSelector {
    fun selectFor(context: Map<String, Any>?): String =
        when (PageDomain.of(context)) {
            PageDomain.HOLDING_ANALYSIS -> {
                DomainSystemPrompts.HOLDING_ANALYSIS
            }
            PageDomain.WATCHLIST -> {
                DomainSystemPrompts.WATCHLIST
            }
            PageDomain.ASSET_REVIEW -> {
                if (isFxPair(context)) DomainSystemPrompts.FX_REVIEW else DomainSystemPrompts.ASSET_REVIEW
            }
            PageDomain.NEWS_SENTIMENT -> {
                DomainSystemPrompts.NEWS_SENTIMENT
            }
            PageDomain.INDEPENDENCE -> {
                DomainSystemPrompts.INDEPENDENCE
            }
            PageDomain.REBALANCE -> {
                DomainSystemPrompts.REBALANCE
            }
            PageDomain.ASSET -> {
                DomainSystemPrompts.ASSET
            }
            PageDomain.WEALTH -> {
                DomainSystemPrompts.WEALTH
            }
            PageDomain.GENERAL -> {
                DomainSystemPrompts.GENERAL
            }
        }
}

/** A currency pair on the FX market: reviewed as a rate, not a company. */
internal fun isFxPair(context: Map<String, Any>?): Boolean =
    (context?.get("market") as? String)?.trim()?.equals(FX_MARKET, ignoreCase = true) == true

private const val FX_MARKET = "FX"