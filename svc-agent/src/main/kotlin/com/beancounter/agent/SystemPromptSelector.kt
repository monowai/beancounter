package com.beancounter.agent

import org.springframework.stereotype.Service

/**
 * Maps a page context to a focused domain system prompt. One prompt per
 * domain keeps each call tight (fewer input tokens) and lets the Anthropic
 * system-prompt cache key on a stable string per domain.
 *
 * See [DomainSystemPrompts] for the prompt content.
 */
@Service
class SystemPromptSelector {
    fun selectFor(context: Map<String, Any>?): String {
        val page = context?.get("page")?.toString()?.lowercase() ?: ""
        return when {
            page.contains("news") && page.contains("sentiment") -> {
                DomainSystemPrompts.NEWS_SENTIMENT
            }
            page.contains("independence") ||
                page.contains("retire") ||
                page.contains("fi") -> {
                DomainSystemPrompts.INDEPENDENCE
            }
            page.contains("rebalanc") -> {
                DomainSystemPrompts.REBALANCE
            }
            page.contains("event") ||
                page.contains("proposed") ||
                page.contains("dividend") ||
                page.contains("trade") ||
                page.contains("trn") -> {
                DomainSystemPrompts.ASSET
            }
            page.contains("holding") ||
                page.contains("portfolio") ||
                page.contains("wealth") ||
                page.contains("position") -> {
                DomainSystemPrompts.WEALTH
            }
            page.isEmpty() -> {
                DomainSystemPrompts.GENERAL
            }
            else -> {
                DomainSystemPrompts.GENERAL
            }
        }
    }
}