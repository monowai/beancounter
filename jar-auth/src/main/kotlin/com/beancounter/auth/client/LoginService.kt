@file:Suppress("PropertyName")

package com.beancounter.auth.client

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
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
    val authConfig: AuthConfig,
    private val jwtTokenCacheService: JwtTokenCacheService
) {
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
        client_id = authConfig.clientId,
        username = user,
        password = password,
        audience = authConfig.audience,
        client_secret = authConfig.clientSecret
    )

    /**
     * m2m login using preconfigured secret with intelligent caching and automatic refresh.
     *
     * Returns token if the call is successful. Uses JwtTokenCacheService for:
     * - Expiry-aware caching
     * - Automatic token refresh on expiry
     * - Proactive refresh (48 hours before expiry)
     *
     * @return token
     */
    fun loginM2m(secretIn: String = authConfig.clientSecret): OpenIdResponse {
        if ("not-set" == secretIn) {
            throw UnauthorizedException("Client Secret is not set")
        }

        val cacheKey = "m2m:${authConfig.clientId}:$secretIn"

        return jwtTokenCacheService
            .getValidToken(cacheKey) {
                val login =
                    ClientCredentialsRequest(
                        client_secret = secretIn,
                        client_id = authConfig.clientId,
                        audience = authConfig.audience
                    )
                log.trace("m2mLogin: ${authConfig.clientId}")
                authGateway.login(login)
            }.also { response ->
                setAuthContext(response)
            }
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
     * Handle JWT expiry by refreshing the token and retrying the operation.
     * This method should be called when a JwtException (typically expired token) is caught.
     *
     * @param secretIn The client secret to use for refresh
     * @param operation The operation to retry after token refresh
     * @return The result of the operation
     */
    fun <T> handleJwtExpiryAndRetry(
        secretIn: String = authConfig.clientSecret,
        operation: () -> T
    ): T {
        try {
            // Clear the cached token to force refresh
            val cacheKey = "m2m:${authConfig.clientId}:$secretIn"
            jwtTokenCacheService.clearToken(cacheKey)

            // Get a fresh token
            loginM2m(secretIn)

            // Retry the operation
            return operation()
        } catch (e: Exception) {
            log.error("Failed to refresh token and retry operation", e)
            throw e
        }
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
        var client_id: String,
        var username: String,
        var password: String,
        var grant_type: String = "password",
        var audience: String,
        var client_secret: String,
        var scope: String = "openid profile email beancounter beancounter:user beancounter:admin"
    ) : AuthRequest

    /**
     * M2M request configured from environment.
     */
    data class ClientCredentialsRequest(
        var client_id: String,
        var client_secret: String = "not-set",
        var audience: String,
        var scope: String = "beancounter beancounter:system",
        var grant_type: String = AuthorizationGrantType.CLIENT_CREDENTIALS.value
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