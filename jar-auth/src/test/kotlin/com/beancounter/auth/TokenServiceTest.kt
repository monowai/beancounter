package com.beancounter.auth

import com.beancounter.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * TokenService verification.
 */
@SpringBootTest(classes = [TokenService::class, MockAuthConfig::class])
@AutoConfigureMockAuth
class TokenServiceTest {
    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun is_BearerToken() {
        assertThat(tokenService.getBearerToken("Test")).isEqualTo("Bearer Test")
    }

    @Test
    fun is_BearerTokenBearing() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }

    @Test
    fun noSecurityContextIsUnauthorized() {
        mockAuthConfig.resetAuth()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }

    @Test
    fun validSecurityContextIsAuthorized() {
        mockAuthConfig.mockLogin("anything here will do")
        assertThat(tokenService.bearerToken).isNotNull
    }
}
