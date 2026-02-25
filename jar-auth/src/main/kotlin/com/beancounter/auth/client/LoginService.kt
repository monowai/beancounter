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
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

/**
 * OpenID client-credential login service to service M2M authentication requests.
 * Uses RestTemplate for form-urlencoded requests to Auth0.
 */
@Service
@ConditionalOnProperty(
    value = ["auth.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class LoginService(
    val jwtDecoder: JwtDecoder,
    val authConfig: AuthConfig
) {
    @Lazy
    @Autowired
    private lateinit var self: LoginService
    private val log = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate()

    fun login(
        user: String,
        password: String
    ): OpenIdResponse {
        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "password")
                add("client_id", authConfig.clientId)
                add("client_secret", authConfig.clientSecret)
                add("audience", authConfig.audience)
                add("username", user)
                add("password", password)
                add("scope", "openid profile email beancounter beancounter:user beancounter:admin")
            }
        val response = postToAuth0(formData)
        log.debug("Logged in $user")
        return response
    }

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
            log.error(
                "M2M login failed: client secret is 'not-set'. " +
                    "Check AUTH_CLIENT_SECRET or SPRING_SECURITY_OAUTH2_REGISTRATION_CUSTOM_CLIENT_SECRET env vars"
            )
            throw UnauthorizedException("Client Secret is not set")
        }
        log.info(
            "M2M login: clientId={}, audience={}, secret={}...{}",
            authConfig.clientId,
            authConfig.audience,
            secretIn.take(4),
            secretIn.takeLast(4)
        )
        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", AuthorizationGrantType.CLIENT_CREDENTIALS.value)
                add("client_id", authConfig.clientId)
                add("client_secret", secretIn)
                add("audience", authConfig.audience)
                add("scope", "beancounter beancounter:system")
            }
        return setAuthContext(postToAuth0(formData))
    }

    private fun postToAuth0(formData: MultiValueMap<String, String>): OpenIdResponse {
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
            }
        val request = HttpEntity(formData, headers)
        val tokenUrl = "${authConfig.issuer}oauth/token"

        try {
            val response =
                restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    OpenIdResponse::class.java
                )
            return response.body
                ?: throw UnauthorizedException("Empty response from Auth0")
        } catch (e: RestClientException) {
            log.error("Auth0 token request failed: {}", e.message, e)
            throw UnauthorizedException("Authentication failed: ${e.message}", e)
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
     * Clear the JWT token cache when tokens expire
     */
    @CacheEvict("auth.m2m", allEntries = true)
    fun clearTokenCache() {
        log.info("Cleared JWT token cache due to expiry")
    }

    /**
     * Simple retry mechanism for JWT expiry.
     * Catches JWT exceptions and UnauthorizedException, clears cache, gets fresh token, and retries operation.
     * UnauthorizedException is thrown when the cached M2M token has expired from the security context.
     */
    fun <T> retryOnJwtExpiry(operation: () -> T): T =
        try {
            operation()
        } catch (e: JwtException) {
            log.info("JWT expired: ${e.message}, refreshing token and retrying...")
            self.clearTokenCache()
            self.loginM2m()
            operation()
        } catch (e: UnauthorizedException) {
            log.info("Unauthorized: ${e.message}, refreshing token and retrying...")
            self.clearTokenCache()
            self.loginM2m()
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
}