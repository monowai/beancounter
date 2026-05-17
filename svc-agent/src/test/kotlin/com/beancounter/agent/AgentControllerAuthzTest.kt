package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.auth.model.AuthConstants
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Contract for [AgentScopeAuthorizer]:
 *
 *  | Caller scope            | `context.page = "Asset Review"` | `context.page = "Portfolio AI"` |
 *  |-------------------------|--------------------------------|--------------------------------|
 *  | beancounter:ai          | allow                          | allow                          |
 *  | beancounter:preview     | allow                          | DENY                           |
 *  | beancounter:system      | allow                          | allow                          |
 *  | beancounter:user (only) | DENY                           | DENY                           |
 *
 * News & Sentiment popup is also preview-eligible (single-asset surface).
 */
private const val PAGE_ASSET_REVIEW = "Asset Review"
private const val PAGE_NEWS_SENTIMENT = "News Sentiment"
private const val PAGE_PORTFOLIO_AI = "Portfolio AI Overview"

class AgentControllerAuthzTest {
    private val authorizer = AgentScopeAuthorizer()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(vararg authorities: String) {
        SecurityContextHolder
            .getContext()
            .authentication =
            TestingAuthenticationToken(
                "test-user",
                "n/a",
                authorities.map(::SimpleGrantedAuthority)
            )
    }

    @Test
    fun `ai scope authorizes any agent context`() {
        authenticateAs(AuthConstants.SCOPE_AI)
        authorizer.authorize(mapOf("page" to PAGE_ASSET_REVIEW))
        authorizer.authorize(mapOf("page" to PAGE_PORTFOLIO_AI))
        authorizer.authorize(null)
    }

    @Test
    fun `system scope authorizes any agent context`() {
        authenticateAs(AuthConstants.SCOPE_SYSTEM)
        authorizer.authorize(mapOf("page" to PAGE_ASSET_REVIEW))
        authorizer.authorize(mapOf("page" to "Independence"))
        authorizer.authorize(null)
    }

    @Test
    fun `preview scope authorizes only the single-asset surfaces`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)

        // Allowed surfaces — case-insensitive, both spellings accepted.
        authorizer.authorize(mapOf("page" to PAGE_ASSET_REVIEW))
        authorizer.authorize(mapOf("page" to "asset-review"))
        authorizer.authorize(mapOf("page" to PAGE_NEWS_SENTIMENT))

        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to PAGE_PORTFOLIO_AI))
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to "Independence"))
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            authorizer.authorize(null)
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `user-only scope is rejected on every agent surface`() {
        authenticateAs(AuthConstants.SCOPE_USER, AuthConstants.SCOPE_BC)

        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to PAGE_ASSET_REVIEW))
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to PAGE_PORTFOLIO_AI))
        }.isInstanceOf(AccessDeniedException::class.java)

        assertThatThrownBy {
            authorizer.authorize(null)
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `unauthenticated context is rejected`() {
        SecurityContextHolder.clearContext()
        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to PAGE_ASSET_REVIEW))
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `requiredFor returns preview-tier set for asset and news surfaces only`() {
        assertThat(authorizer.requiredFor(mapOf("page" to PAGE_ASSET_REVIEW)))
            .contains(AuthConstants.SCOPE_PREVIEW)
        assertThat(authorizer.requiredFor(mapOf("page" to "asset-review")))
            .contains(AuthConstants.SCOPE_PREVIEW)
        assertThat(authorizer.requiredFor(mapOf("page" to PAGE_NEWS_SENTIMENT)))
            .contains(AuthConstants.SCOPE_PREVIEW)

        assertThat(authorizer.requiredFor(mapOf("page" to PAGE_PORTFOLIO_AI)))
            .doesNotContain(AuthConstants.SCOPE_PREVIEW)
        assertThat(authorizer.requiredFor(null))
            .doesNotContain(AuthConstants.SCOPE_PREVIEW)
    }

    @Test
    fun `partial keyword matches do not grant preview access`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)
        // Substring matches like "asset review notes" or "weekly news + sentiment summary"
        // must NOT be treated as preview-eligible — only exact normalised pages do.
        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to "asset review notes"))
        }.isInstanceOf(AccessDeniedException::class.java)
        assertThatThrownBy {
            authorizer.authorize(mapOf("page" to "weekly news and sentiment summary"))
        }.isInstanceOf(AccessDeniedException::class.java)
    }
}