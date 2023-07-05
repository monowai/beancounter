package com.beancounter.auth

import com.beancounter.auth.TokenService.Companion.BEARER
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
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
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

/**
 * Can the login service authenticate the user using OAuth?
 */
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    FeignAutoConfiguration::class,
    OAuthConfig::class,
)
@ActiveProfiles("mockauth")
@AutoConfigureMockAuth
@SpringBootTest(
    classes = [
        ClientPasswordConfig::class, MockAuthConfig::class,
        TokenService::class, AuthUtilService::class, TokenUtils::class,
    ],
)
class LoginServiceTest {

    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Mock
    private lateinit var authGateway: LoginService.AuthGateway

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var authUtilService: AuthUtilService

    @BeforeEach
    fun mockJwtService() {
        loginService = LoginService(
            authGateway,
            jwtDecoder = mockAuthConfig.jwtDecoder,
            mockAuthConfig.authConfig,
        )
    }

    @Test
    fun is_m2mLoginWorking() {
        val token = authUtilService.authenticateM2M(SystemUser())
        Mockito.`when`(
            authGateway.login(
                LoginService.ClientCredentialsRequest(
                    mockAuthConfig.authConfig.clientId,
                    mockAuthConfig.authConfig.clientSecret,
                    mockAuthConfig.authConfig.audience,
                ),
            ),
        ).thenReturn(
            OpenIdResponse(
                token.token.tokenValue,
                "beancounter beancounter:system",
                Duration.ofSeconds(20_000).seconds,
                BEARER,
            ),
        )

        loginService.loginM2m().token.isNotEmpty()
        assertTrue(tokenService.isServiceToken)
    }

    @Test
    fun is_accountWithNoRulesNeitherServiceOrUser() {
        val token = authUtilService.authenticateNoRoles(SystemUser())
        Mockito.`when`(
            authGateway.login(
                LoginService.ClientCredentialsRequest(
                    mockAuthConfig.authConfig.clientId,
                    mockAuthConfig.authConfig.clientSecret,
                    mockAuthConfig.authConfig.audience,
                ),
            ),
        ).thenReturn(
            OpenIdResponse(
                token.token.tokenValue,
                "beancounter beancounter:system",
                Duration.ofSeconds(20_000).seconds,
                BEARER,
            ),
        )

        loginService.loginM2m().token.isNotEmpty()
        assertFalse(tokenService.isServiceToken)
    }

    @Test
    fun is_userLoginWorking() {
        val systemUser = SystemUser()
        val token = authUtilService.authenticate(systemUser)
        val response = BcJson().objectMapper.readValue(
            ClassPathResource("user-token-response.json").file,
            OpenIdResponse::class.java,
        )

        Mockito.`when`(mockAuthConfig.jwtDecoder.decode(systemUser.email))
            .thenReturn(token.token)
        Mockito.`when`(authGateway.login(loginService.passwordRequest("user", "password")))
            .thenReturn(response)
        loginService.login("user", "password").token.isNotEmpty()
        assertFalse(tokenService.isServiceToken)
    }

    @Test
    fun is_notSetFailing() {
        assertThrows(UnauthorizedException::class.java) {
            loginService.loginM2m("not-set")
        }
    }

    @Test
    fun is_serviceTokenWorking() {
        authUtilService.authenticateM2M(
            SystemUser(email = ""),
            AuthUtilService.AuthProvider.AUTH0,
        )
        assertTrue(tokenService.isServiceToken)
    }
}
