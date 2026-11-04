package com.beancounter.marketdata.apikey

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * A new key's scopes are bounded by the caller's own token.
 */
class ApiKeyScopesTest {
    private val apiKeyScopes = ApiKeyScopes()

    private fun signIn(vararg scopes: String) {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("user", null, *scopes.map { "SCOPE_$it" }.toTypedArray())
    }

    @AfterEach
    fun signOut() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should default to every issuable scope the caller holds`() {
        signIn(AuthConstants.USER, AuthConstants.AI, AuthConstants.ADMIN)

        assertThat(apiKeyScopes.validated(emptyList()))
            .containsExactly(AuthConstants.USER, AuthConstants.AI)
    }

    @Test
    fun `should refuse an issuable scope the caller's token lacks`() {
        signIn(AuthConstants.USER)

        assertThatThrownBy { apiKeyScopes.validated(listOf(AuthConstants.AI)) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(AuthConstants.AI)
    }

    @Test
    fun `should never issue the admin scope`() {
        signIn(AuthConstants.USER, AuthConstants.ADMIN)

        assertThatThrownBy { apiKeyScopes.validated(listOf(AuthConstants.ADMIN)) }
            .isInstanceOf(BusinessException::class.java)
    }
}