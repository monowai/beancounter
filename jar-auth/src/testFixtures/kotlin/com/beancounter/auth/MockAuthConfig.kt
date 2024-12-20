package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.Registration
import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mock
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Mock out JWT auth classes, to allow for token based testing that can
 * assert endpoints are secured appropriately.
 *
 * .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuth.getUserToken()))
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
    @Mock
    lateinit var oAuthConfig: OAuthConfig

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var authConfig: AuthConfig

    @MockBean
    lateinit var authGateway: LoginService.AuthGateway

    lateinit var tokenUtils: TokenUtils

    @Autowired
    fun getTokenUtils(authConfig: AuthConfig): TokenUtils {
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
        val jwt = getUserToken(systemUser)
        Mockito.`when`(jwtDecoder.decode(systemUser.email)).thenReturn(jwt)
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwtDecoder.decode(systemUser.email))

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

    fun resetAuth() {
        SecurityContextHolder.getContext().authentication = null
    }
}