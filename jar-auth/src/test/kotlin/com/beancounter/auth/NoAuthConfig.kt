package com.beancounter.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Service

/**
 * Handles the scenario where you want absolutely no Auth in your config.
 */
@Service
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "false")
class NoAuthConfig {
    @MockBean
    lateinit var oAuthConfig: OAuthConfig

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @MockBean
    lateinit var tokenService: TokenService
}
