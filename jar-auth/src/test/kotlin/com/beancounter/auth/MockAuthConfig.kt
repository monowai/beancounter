package com.beancounter.auth

import com.beancounter.auth.client.LoginService
import com.beancounter.common.model.SystemUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service

/**
 * Mock out JWT auth classes, to allow for token based testing that can
 * assert endpoints are secured appropriately.
 *
 * .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuth.getUserToken()))
 */
@Service
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "true", matchIfMissing = false)
@Import(AuthConfig::class, LoginService::class, TokenService::class, TokenUtils::class)
class MockAuthConfig {

    @MockBean
    lateinit var oAuthConfig: OAuthConfig

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var authConfig: AuthConfig

    @Autowired
    lateinit var loginService: LoginService

    @Autowired
    lateinit var tokenService: TokenService

    @MockBean
    lateinit var authGateway: LoginService.AuthGateway

    lateinit var tokenUtils: TokenUtils

    @Autowired
    fun setDefaultObjects(authConfig: AuthConfig) {
        this.tokenUtils = TokenUtils(authConfig)
    }

    fun getUserToken(systemUser: SystemUser = SystemUser("user", "user@testing.com")): Jwt {
        return tokenUtils.getUserToken(systemUser)
    }
}
