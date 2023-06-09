package com.beancounter.auth

import com.beancounter.auth.AuthUtils.Companion.mockOpenConnect
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.common.exception.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles

/**
 * Can the login service authenticate the user using OAuth?
 */
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    FeignAutoConfiguration::class,
    OAuthConfig::class,
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
@SpringBootTest(classes = [ClientPasswordConfig::class, OAuthConfig::class])
class LoginServiceTest {

    @Autowired
    private lateinit var oAuthConfig: OAuthConfig

    @Autowired
    private lateinit var loginService: LoginService

    @MockBean
    private lateinit var cacheManager: CacheManager

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var authGateway: LoginService.AuthGateway

    @Autowired
    private lateinit var authConfig: AuthConfig

    @BeforeEach
    fun mockJwtService() {
        Mockito.`when`(jwtDecoder.decode(ArgumentMatchers.any()))
            .thenReturn(Mockito.mock(Jwt::class.java))

        mockOpenConnect(authConfig)
    }

    @Test
    fun is_m2mLoginWorking() {
        loginService.loginM2m().token.isNotEmpty()
    }

    @Test
    fun is_userLoginWorking() {
        loginService.login("user", "password").token.isNotEmpty()
    }

    @Test
    fun is_notSetFailing() {
        assertThrows(UnauthorizedException::class.java) {
            loginService.loginM2m("not-set")
        }
    }
}
