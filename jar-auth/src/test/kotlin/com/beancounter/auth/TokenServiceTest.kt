package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test suite for TokenService to ensure proper token handling and validation.
 *
 * This class tests:
 * - Bearer token formatting and extraction
 * - Token validation and security context handling
 * - Unauthorized access scenarios
 * - Token service integration with authentication
 *
 * Tests verify that the TokenService can properly handle JWT tokens
 * and manage authentication state.
 */
@SpringBootTest(classes = [TokenService::class, MockAuthConfig::class])
@AutoConfigureMockAuth
class TokenServiceTest {
    @Autowired
    private lateinit var tokenService: TokenService

    @MockitoBean
    lateinit var authGateway: LoginService.AuthGateway

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun `should format bearer token correctly`() {
        assertThat(tokenService.getBearerToken("Test")).isEqualTo("Bearer Test")
    }

    @Test
    fun `should throw UnauthorizedException when no bearer token is available`() {
        mockAuthConfig.logout()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }

    @Test
    fun `should throw UnauthorizedException when no security context is available`() {
        mockAuthConfig.logout()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }
}