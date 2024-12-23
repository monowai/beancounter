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
 * TokenService verification.
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
    fun is_BearerToken() {
        assertThat(tokenService.getBearerToken("Test")).isEqualTo("Bearer Test")
    }

    @Test
    fun is_BearerTokenBearing() {
        mockAuthConfig.logout()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }

    @Test
    fun noSecurityContextIsUnauthorized() {
        mockAuthConfig.logout()
        assertThrows(UnauthorizedException::class.java) { tokenService.bearerToken }
    }
}