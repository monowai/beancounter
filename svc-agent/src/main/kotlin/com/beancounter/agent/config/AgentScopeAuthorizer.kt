package com.beancounter.agent.config

import com.beancounter.auth.model.AuthConstants
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Per-call authorization for the agent endpoints.
 *
 * The single `/agent/query` endpoint serves two tiers:
 *
 *  - Default tier — full AI review (portfolio overview, chat, retirement, rebalance, etc.)
 *    requires `beancounter:ai` (or `beancounter:system` for M2M).
 *  - Preview tier — single-asset surfaces (Asset Review popup and the per-holding
 *    News & Sentiment popup) additionally accept `beancounter:preview` so default users
 *    can sample the AI features without a paid upgrade.
 *
 * The split is body-driven (the LLM tool routing already keys off `context.page`,
 * see [com.beancounter.agent.tools.ToolSelector]), so authz is applied programmatically
 * inside the controller rather than via `@PreAuthorize`.
 */
@Component
class AgentScopeAuthorizer {
    fun authorize(context: Map<String, Any>?) {
        val required = requiredFor(context)
        val granted =
            SecurityContextHolder
                .getContext()
                .authentication
                ?.authorities
                ?.map { it.authority }
                ?.toSet()
                .orEmpty()
        if (granted.intersect(required).isEmpty()) {
            throw AccessDeniedException(
                "Agent query requires one of: ${required.joinToString(", ")}"
            )
        }
    }

    internal fun requiredFor(context: Map<String, Any>?): Set<String> {
        val normalizedPage =
            context
                ?.get("page")
                ?.toString()
                ?.trim()
                ?.lowercase()
                ?.replace('-', ' ')
                ?.replace(WHITESPACE, " ")
                .orEmpty()
        return if (normalizedPage in PREVIEW_PAGES) {
            setOf(
                AuthConstants.SCOPE_AI,
                AuthConstants.SCOPE_PREVIEW,
                AuthConstants.SCOPE_SYSTEM
            )
        } else {
            setOf(
                AuthConstants.SCOPE_AI,
                AuthConstants.SCOPE_SYSTEM
            )
        }
    }

    companion object {
        // Exact-match allowlist of preview-eligible page values. The
        // normalisation step (trim, lowercase, hyphen→space, whitespace
        // collapse) lets the bc-view side send "Asset Review" / "asset-review"
        // and have authz still resolve to the same key. Substring matching
        // is deliberately avoided — a page literal like "asset review notes"
        // would otherwise grant preview access to a non-preview surface.
        private val PREVIEW_PAGES = setOf("asset review", "news sentiment")
        private val WHITESPACE = Regex("\\s+")
    }
}