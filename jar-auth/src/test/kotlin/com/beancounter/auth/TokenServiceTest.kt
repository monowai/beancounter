package com.beancounter.auth

import com.beancounter.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var authConfig: AuthConfig

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

    @Test
    fun `should return system user id from configured claim`() {
        val jwt =
            Jwt
                .withTokenValue("test")
                .header("alg", "none")
                .subject("auth0|user")
                .claim(authConfig.claimSystemUserId, "SU-12345")
                .claim("scope", "beancounter beancounter:user")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        assertThat(tokenService.getSystemUserId()).isEqualTo("SU-12345")
    }

    @Test
    fun `should return null when system user id claim is absent`() {
        val jwt =
            Jwt
                .withTokenValue("test")
                .header("alg", "none")
                .subject("auth0|user")
                .claim("scope", "beancounter beancounter:user")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        assertThat(tokenService.getSystemUserId()).isNull()
    }

    @Test
    fun `should return null when system user id claim is blank`() {
        val jwt =
            Jwt
                .withTokenValue("test")
                .header("alg", "none")
                .subject("auth0|user")
                .claim(authConfig.claimSystemUserId, "")
                .claim("scope", "beancounter beancounter:user")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        assertThat(tokenService.getSystemUserId()).isNull()
    }
}