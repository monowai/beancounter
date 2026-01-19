package com.beancounter.auth.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for AuthConstants.
 */
class AuthConstantsTest {
    private fun createJwt(scope: String?): Jwt {
        val builder =
            Jwt
                .withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .subject("test-user")

        if (scope != null) {
            builder.claim("scope", scope)
        }
        return builder.build()
    }

    @Test
    fun `isAdmin returns true when scope contains admin`() {
        val jwt = createJwt("beancounter profile email beancounter:admin beancounter:user")
        assertThat(AuthConstants.isAdmin(jwt)).isTrue()
    }

    @Test
    fun `isAdmin returns false when scope does not contain admin`() {
        val jwt = createJwt("beancounter profile email beancounter:user")
        assertThat(AuthConstants.isAdmin(jwt)).isFalse()
    }

    @Test
    fun `isAdmin returns false when scope is null`() {
        val jwt = createJwt(null)
        assertThat(AuthConstants.isAdmin(jwt)).isFalse()
    }

    @Test
    fun `isAdmin returns false when scope is empty`() {
        val jwt = createJwt("")
        assertThat(AuthConstants.isAdmin(jwt)).isFalse()
    }

    @Test
    fun `isAdmin returns true when scope contains only admin`() {
        val jwt = createJwt(AuthConstants.ADMIN)
        assertThat(AuthConstants.isAdmin(jwt)).isTrue()
    }
}