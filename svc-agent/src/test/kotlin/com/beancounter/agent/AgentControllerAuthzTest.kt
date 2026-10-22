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
 *  | Caller scope            | any page | metered (weekly quota)              |
 *  |-------------------------|----------|-------------------------------------|
 *  | beancounter:ai          | allow    | never                               |
 *  | beancounter:system      | allow    | never                               |
 *  | beancounter:preview     | allow    | yes, except a one-off News query    |
 *  | beancounter:user (only) | DENY     | -                                   |
 */
private const val PAGE_ASSET_REVIEW = "Asset Review"
private const val PAGE_NEWS_SENTIMENT = "News & Sentiment"
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

    private fun on(page: String?) = AgentQuery("q", context = page?.let { mapOf("page" to it) })

    @Test
    fun `ai scope authorizes any agent context and is never metered`() {
        authenticateAs(AuthConstants.SCOPE_AI)
        authorizer.authorize()
        assertThat(authorizer.metered(on(PAGE_PORTFOLIO_AI))).isFalse()
    }

    @Test
    fun `system scope authorizes any agent context and is never metered`() {
        authenticateAs(AuthConstants.SCOPE_SYSTEM)
        authorizer.authorize()
        assertThat(authorizer.metered(on("Independence"))).isFalse()
    }

    @Test
    fun `preview scope authorizes every agent surface, metered`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)

        authorizer.authorize()

        assertThat(authorizer.metered(on(PAGE_PORTFOLIO_AI))).isTrue()
        assertThat(authorizer.metered(on(PAGE_ASSET_REVIEW))).isTrue()
        assertThat(authorizer.metered(on(null))).isTrue()
    }

    @Test
    fun `a one-off news query is free, in either spelling`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)

        assertThat(authorizer.metered(on(PAGE_NEWS_SENTIMENT))).isFalse()
        assertThat(authorizer.metered(on("news-sentiment"))).isFalse()
    }

    @Test
    fun `a news page turn in a conversation or with history is metered`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)

        assertThat(authorizer.metered(on(PAGE_NEWS_SENTIMENT).copy(conversationId = "c1"))).isTrue()
        assertThat(
            authorizer.metered(on(PAGE_NEWS_SENTIMENT).copy(history = listOf(ChatTurn("user", "earlier"))))
        ).isTrue()
    }

    @Test
    fun `user-only scope is rejected on every agent surface`() {
        authenticateAs(AuthConstants.SCOPE_USER, AuthConstants.SCOPE_BC)

        assertThatThrownBy {
            authorizer.authorize()
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `unauthenticated context is rejected`() {
        SecurityContextHolder.clearContext()
        assertThatThrownBy {
            authorizer.authorize()
        }.isInstanceOf(AccessDeniedException::class.java)
    }

    @Test
    fun `a preview caller is verified only with a confirmed email`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)
        assertThat(authorizer.verified()).isFalse()

        authenticateAs(AuthConstants.SCOPE_PREVIEW, AuthConstants.SCOPE_VERIFIED)
        assertThat(authorizer.verified()).isTrue()
    }

    @Test
    fun `ai and system callers need no confirmed email`() {
        authenticateAs(AuthConstants.SCOPE_AI)
        assertThat(authorizer.verified()).isTrue()

        authenticateAs(AuthConstants.SCOPE_SYSTEM)
        assertThat(authorizer.verified()).isTrue()
    }

    @Test
    fun `a one-off news page is free however the client spells it`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)

        assertThat(authorizer.metered(on("News and Sentiment"))).isFalse()
        assertThat(authorizer.metered(on("news-sentiment"))).isFalse()
        assertThat(authorizer.metered(on("Holding Analysis news sentiment"))).isTrue()
    }
}