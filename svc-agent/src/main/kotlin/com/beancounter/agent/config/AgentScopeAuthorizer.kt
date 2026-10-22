package com.beancounter.agent.config

import com.beancounter.agent.AgentQuery
import com.beancounter.agent.normalisePage
import com.beancounter.auth.model.AuthConstants
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Per-call authorization for the agent endpoints.
 *
 * Every page is open to `beancounter:preview` (the default role),
 * `beancounter:ai` and `beancounter:system`. A preview caller is metered:
 * their turns count against a weekly quota (see
 * [com.beancounter.agent.quota.AgentQuota]), except a one-off News & Sentiment
 * lookup. `beancounter:ai` and M2M callers are never metered.
 */
@Component
class AgentScopeAuthorizer {
    fun authorize() {
        if (granted().intersect(ALLOWED).isEmpty()) {
            throw AccessDeniedException("Agent query requires one of: ${ALLOWED.joinToString(", ")}")
        }
    }

    /** True when the caller has no quota to count against. */
    fun unlimited(): Boolean = granted().intersect(UNLIMITED).isNotEmpty()

    /**
     * True when [body] counts against the caller's quota. A News & Sentiment
     * lookup is free only as a one-off: a turn in a conversation or carrying
     * history is a chat, whatever page it names.
     */
    fun metered(body: AgentQuery): Boolean {
        if (unlimited()) return false
        val oneOff = body.conversationId == null && body.history.isNullOrEmpty()
        return !(oneOff && normalisePage(body.context?.get("page")) in FREE_PAGES)
    }

    private fun granted(): Set<String> =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.authorities
            ?.mapNotNull { it.authority }
            ?.toSet()
            .orEmpty()

    companion object {
        private val UNLIMITED = setOf(AuthConstants.SCOPE_AI, AuthConstants.SCOPE_SYSTEM)
        private val ALLOWED = UNLIMITED + AuthConstants.SCOPE_PREVIEW

        // Exact-match allowlist of normalised pages (see normalisePage); bc-view
        // sends "News & Sentiment". Substring matching would let any page named
        // like news run free.
        private val FREE_PAGES = setOf("news sentiment", "news & sentiment")
    }
}