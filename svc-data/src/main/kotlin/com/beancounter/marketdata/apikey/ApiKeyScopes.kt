package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.BusinessException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Which scopes a new API key may carry. A key never carries a scope its
 * owner's sign-in lacks, so a default-role user cannot mint their way past the
 * AI quota with `beancounter:ai`.
 */
@Component
class ApiKeyScopes {
    /**
     * The scopes a new key carries: those asked for, or by default every
     * issuable scope the caller's own token holds.
     */
    fun validated(requested: Collection<String>): Collection<String> {
        val issuable = ISSUABLE_SCOPES.intersect(callerScopes())
        val scopes = requested.ifEmpty { issuable }
        val invalid = scopes.filterNot { it in issuable }
        if (invalid.isNotEmpty()) {
            throw BusinessException(
                "Scope(s) not permitted for API keys: ${invalid.joinToString(", ")}"
            )
        }
        return scopes
    }

    private fun callerScopes(): Set<String> =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.authorities
            ?.mapNotNull { it.authority?.takeIf { name -> name.startsWith(SCOPE_PREFIX) }?.removePrefix(SCOPE_PREFIX) }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val SCOPE_PREFIX = "SCOPE_"

        // Scopes issuable via API key, each only to a caller whose token holds
        // it. beancounter:admin and beancounter:system are never issuable this way, and
        // beancounter:widget comes only from [ApiKeyService.createWidget].
        val ISSUABLE_SCOPES =
            linkedSetOf(
                AuthConstants.APP_NAME,
                AuthConstants.USER,
                AuthConstants.PREVIEW,
                AuthConstants.AI,
                AuthConstants.VERIFIED
            )
    }
}