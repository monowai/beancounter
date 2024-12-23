package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.Registration
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Support config, to allow for token based testing that can
 * assert endpoints are secured appropriately.
 *
 *
 *  @MockitoBean
 *  late init var authGateway: LoginService.AuthGateway
 *
 *  @MockitoBean
 *  late init var jwtDecoder: JwtDecoder
 *
 *  @AutoConfigureMockAuth
 *  class MarketMvcTests {
 *
 *  @Test
 *  fun `all markets found for logged in user`() {
 *      token = mockAuthConfig.login("MarketMvcTests@testing.com")
 *      mockMvc.perform(MockMvcRequestBuilders
 *           .get("/markets")
 *           .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
 * }
 */
@Service
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
@Import(
    AuthConfig::class,
    LoginService::class,
    TokenService::class,
    TokenUtils::class,
    AuthUtilService::class
)
class MockAuthConfig {
    lateinit var tokenUtils: TokenUtils

    @Autowired
    private lateinit var authConfig: AuthConfig

    @Autowired
    fun tokenUtils(authConfig: AuthConfig): TokenUtils {
        this.tokenUtils = TokenUtils(authConfig)
        return this.tokenUtils
    }

    fun getUserToken(systemUser: SystemUser = SystemUser(email = "user@testing.com")): Jwt =
        tokenUtils.getSystemUserToken(systemUser)

    fun login(email: String = "test@nowhere.com"): Jwt =
        login(
            SystemUser(
                email,
                email,
                auth0 = "auth0"
            ),
            null
        )

    /**
     * Log the user in, optionally registering them if an ISystemUser is supplied
     */
    fun login(
        systemUser: SystemUser,
        registrationService: Registration?
    ): Jwt {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(decode(systemUser.email))

        val token = getUserToken(systemUser)
        assertThat(token)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "subject",
                systemUser.id
            )
        registrationService?.register(systemUser)
        return token
    }

    fun logout() {
        SecurityContextHolder.getContext().authentication = null
    }

    fun decode(token: String): Jwt =
        Jwt
            .withTokenValue(token)
            .header("alg", "none")
            .claim(authConfig.claimEmail, token)
            .claim("sub", "mockUser")
            .claim("permissions", listOf("USER"))
            .build()
}