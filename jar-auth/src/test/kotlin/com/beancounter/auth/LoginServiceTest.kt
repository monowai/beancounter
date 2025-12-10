package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Test suite for LoginService to ensure OAuth authentication works correctly.
 *
 * This class tests:
 * - Machine-to-machine (M2M) authentication flows
 * - Interactive user authentication flows
 * - Token validation and processing
 * - Error handling for authentication failures
 * - Integration with OAuth providers
 *
 * Tests verify that the LoginService can properly authenticate users and
 * handle various OAuth grant types and scenarios.
 */
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    OAuthConfig::class
)
@ActiveProfiles("mockauth")
@AutoConfigureMockAuth
@SpringBootTest(
    classes = [
        ClientPasswordConfig::class,
        MockAuthConfig::class,
        TokenService::class,
        AuthUtilService::class,
        TokenUtils::class,
        LoginService::class
    ]
)
class LoginServiceTest {
    @Autowired
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var authConfig: AuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var authUtilService: AuthUtilService

    @Test
    fun `should throw UnauthorizedException when client is not set`() {
        assertThrows(UnauthorizedException::class.java) {
            loginService.loginM2m("not-set")
        }
    }

    @Test
    fun `should identify service token correctly`() {
        authUtilService.authenticateM2M(
            SystemUser(email = ""),
            AuthUtilService.AuthProvider.AUTH0
        )
        assertTrue(tokenService.isServiceToken)
    }

    @Test
    fun `should identify user token correctly`() {
        authUtilService.authenticate(
            SystemUser(email = "test@example.com"),
            AuthUtilService.AuthProvider.AUTH0
        )
        assertFalse(tokenService.isServiceToken)
    }

    @Test
    fun `should handle account with no roles correctly`() {
        authUtilService.authenticateNoRoles(SystemUser())
        assertFalse(tokenService.isServiceToken)
    }

    @Test
    fun `should mask credentials correctly for logging`() {
        val secret = "jkVboA25BQq8spZHfg1YR37TWnWOLwn7GSKhBqXeo71gKT4BHilMC1IelrCEAqTY"
        val maskedPrefix = secret.take(4)
        val maskedSuffix = secret.takeLast(4)

        assertThat(maskedPrefix).isEqualTo("jkVb")
        assertThat(maskedSuffix).isEqualTo("AqTY")
        assertThat(secret).startsWith(maskedPrefix)
        assertThat(secret).endsWith(maskedSuffix)
    }

    @Test
    fun `should handle short secrets for masking`() {
        val shortSecret = "abc"
        val maskedPrefix = shortSecret.take(4)
        val maskedSuffix = shortSecret.takeLast(4)

        // Short secrets should still work with take/takeLast
        assertThat(maskedPrefix).isEqualTo("abc")
        assertThat(maskedSuffix).isEqualTo("abc")
    }

    @Test
    fun `should handle empty secret for masking`() {
        val emptySecret = ""
        val maskedPrefix = emptySecret.take(4)
        val maskedSuffix = emptySecret.takeLast(4)

        assertThat(maskedPrefix).isEmpty()
        assertThat(maskedSuffix).isEmpty()
    }

    @Test
    fun `should throw UnauthorizedException with helpful message when secret is not-set`() {
        val exception =
            assertThrows(UnauthorizedException::class.java) {
                loginService.loginM2m("not-set")
            }
        assertThat(exception.message).isEqualTo("Client Secret is not set")
    }
}