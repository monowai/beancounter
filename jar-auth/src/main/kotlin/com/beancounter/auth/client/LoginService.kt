@file:Suppress("PropertyName")

package com.beancounter.auth.client

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Lazy
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * OpenID client-credential login service to service M2M authentication requests
 */
@Service
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class LoginService(
    private val authGateway: AuthGateway,
    val jwtDecoder: JwtDecoder,
    val authConfig: AuthConfig
) {
    @Lazy
    @Autowired
    private lateinit var self: LoginService
    private val log = LoggerFactory.getLogger(this::class.java)

    fun login(
        user: String,
        password: String
    ): OpenIdResponse {
        val passwordRequest =
            passwordRequest(
                user,
                password
            )
        val response = authGateway.login(passwordRequest)
        log.debug("Logged in $user")
        return response
    }

    fun passwordRequest(
        user: String,
        password: String
    ) = PasswordRequest(
        clientId = authConfig.clientId,
        username = user,
        password = password,
        audience = authConfig.audience,
        clientSecret = authConfig.clientSecret
    )

    /**
     * Simple M2M login with Spring @Cacheable.
     *
     * Returns cached token if available, otherwise fetches new token.
     * Use retryOnJwtExpiry() wrapper for automatic retry on token expiry.
     *
     * @return token
     */
    @Cacheable("auth.m2m")
    fun loginM2m(secretIn: String = authConfig.clientSecret): OpenIdResponse {
        if ("not-set" == secretIn) {
            throw UnauthorizedException("Client Secret is not set")
        }
        val login =
            ClientCredentialsRequest(
                clientSecret = secretIn,
                clientId = authConfig.clientId,
                audience = authConfig.audience
            )
        log.trace("m2mLogin: ${authConfig.clientId}")
        return setAuthContext(authGateway.login(login))
    }

    fun setAuthContext(response: OpenIdResponse): OpenIdResponse {
        SecurityContextHolder.getContext().authentication = authenticationToken(response)
        return response
    }

    private fun authenticationToken(response: OpenIdResponse) =
        JwtAuthenticationToken(
            jwtDecoder.decode(
                response.token
            )
        )

    /**
     * Clear the JWT token cache when tokens expire
     */
    @CacheEvict("auth.m2m", allEntries = true)
    fun clearTokenCache() {
        log.info("Cleared JWT token cache due to expiry")
    }

    /**
     * Simple retry mechanism for JWT expiry.
     * Catches JWT exceptions, clears cache, gets fresh token, and retries operation.
     */
    fun <T> retryOnJwtExpiry(operation: () -> T): T =
        try {
            operation()
        } catch (jwtException: JwtException) {
            log.warn("JWT operation failed: ${jwtException.message}, refreshing token and retrying...")

            // Clear cache and get fresh token
            clearTokenCache()
            self.loginM2m()

            // Retry the operation
            operation()
        }

    /**
     * Interface to support various oAuth login request types.
     */
    interface AuthRequest

    /**
     * OAuth2 interactive login request. These properties are interpreted literally by Spring, so
     * need the underscores in the variable names otherwise they're not mapped correctly
     */
    data class PasswordRequest(
        @field:com.fasterxml.jackson.annotation.JsonProperty("client_id")
        var clientId: String,
        var username: String,
        var password: String,
        @field:com.fasterxml.jackson.annotation.JsonProperty("grant_type")
        var grantType: String = "password",
        var audience: String,
        @field:com.fasterxml.jackson.annotation.JsonProperty("client_secret")
        var clientSecret: String,
        var scope: String = "openid profile email beancounter beancounter:user beancounter:admin"
    ) : AuthRequest

    /**
     * M2M request configured from environment.
     */
    data class ClientCredentialsRequest(
        @field:com.fasterxml.jackson.annotation.JsonProperty("client_id")
        var clientId: String,
        @field:com.fasterxml.jackson.annotation.JsonProperty("client_secret")
        var clientSecret: String = "not-set",
        var audience: String,
        var scope: String = "beancounter beancounter:system",
        @field:com.fasterxml.jackson.annotation.JsonProperty("grant_type")
        var grantType: String = AuthorizationGrantType.CLIENT_CREDENTIALS.value
    ) : AuthRequest

    /**
     * Obtain a token from BC-DATA that can be used by the client app.
     */
    @FeignClient(
        name = "auth0",
        url = "\${auth.uri:https://beancounter.eu.auth0.com/}"
    )
    @ConditionalOnProperty(
        value = ["auth.enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    interface AuthGateway {
        @PostMapping(
            value = ["oauth/token"],
            consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
            produces = [MediaType.APPLICATION_JSON_VALUE]
        )
        fun login(
            @RequestBody authRequest: AuthRequest
        ): OpenIdResponse
    }
}