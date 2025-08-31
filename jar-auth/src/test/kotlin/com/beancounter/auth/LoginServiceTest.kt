package com.beancounter.auth

import com.beancounter.auth.TokenService.Companion.BEARER
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.core.io.ClassPathResource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration

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
    FeignAutoConfiguration::class,
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
        TokenUtils::class
    ]
)
class LoginServiceTest {
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Mock
    private lateinit var authGateway: LoginService.AuthGateway

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var authUtilService: AuthUtilService

    @BeforeEach
    fun mockJwtService() {
        loginService =
            LoginService(
                authGateway,
                jwtDecoder,
                authConfig
            )
    }

    @Test
    fun `should authenticate machine-to-machine login successfully`() {
        val token = authUtilService.authenticateM2M(SystemUser())
        Mockito
            .`when`(
                authGateway.login(
                    LoginService.ClientCredentialsRequest(
                        authConfig.clientId,
                        authConfig.clientSecret,
                        authConfig.audience
                    )
                )
            ).thenReturn(
                OpenIdResponse(
                    token.token.tokenValue,
                    "beancounter beancounter:system",
                    Duration.ofSeconds(20_000).seconds,
                    BEARER
                )
            )

        loginService.loginM2m().token.isNotEmpty()
        assertTrue(tokenService.isServiceToken)
    }

    @Test
    fun `should handle account with no roles correctly`() {
        val token = authUtilService.authenticateNoRoles(SystemUser())
        Mockito
            .`when`(
                authGateway.login(
                    LoginService.ClientCredentialsRequest(
                        authConfig.clientId,
                        authConfig.clientSecret,
                        authConfig.audience
                    )
                )
            ).thenReturn(
                OpenIdResponse(
                    token.token.tokenValue,
                    "beancounter beancounter:system",
                    Duration.ofSeconds(20_000).seconds,
                    BEARER
                )
            )

        loginService.loginM2m().token.isNotEmpty()
        assertFalse(tokenService.isServiceToken)
    }

    @Test
    fun `should authenticate user login successfully`() {
        val systemUser = SystemUser()
        val token = authUtilService.authenticate(systemUser)
        val response =
            objectMapper.readValue<OpenIdResponse>(
                ClassPathResource("user-token-response.json").file
            )

        Mockito
            .`when`(jwtDecoder.decode(systemUser.email))
            .thenReturn(token.token)
        Mockito
            .`when`(
                authGateway.login(
                    loginService.passwordRequest(
                        "user",
                        "password"
                    )
                )
            ).thenReturn(response)
        loginService
            .login(
                "user",
                "password"
            ).token
            .isNotEmpty()
        assertFalse(tokenService.isServiceToken)
    }

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
}