package com.beancounter.admin

import de.codecentric.boot.admin.server.domain.entities.Instance
import de.codecentric.boot.admin.server.web.client.HttpHeadersProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * Attaches a bearer token to every outbound call SBA makes to a registered
 * service's actuator. The BC services gate the actuator endpoints on
 * `SCOPE_beancounter:admin` or `SCOPE_beancounter:system` (see
 * `jar-auth/WebAuthFilterConfig.kt`).
 *
 * Token sources, in order:
 *  1. The currently authenticated OIDC user's access_token (when the call
 *     happens inside a request thread carrying a Spring SecurityContext).
 *  2. The most recently observed user token, cached after each interactive
 *     request so background polling threads still have something to send.
 *  3. An optional static token from `BC_ADMIN_M2M_TOKEN` — fallback for
 *     "no admin has ever logged in this boot" scenarios.
 *
 * This means continuous polling depends on at least one admin having
 * recently visited the UI. When the cached token expires Auth0-side, the
 * dashboard flips instances to OFFLINE until the next login refreshes it.
 * Acceptable for an admin-only tool; swap in a true client_credentials cache
 * if continuous monitoring becomes a hard requirement.
 */
@Component
class BearerTokenHttpHeadersProvider(
    private val authorizedClientService: OAuth2AuthorizedClientService,
    @Value("\${beancounter.admin.client.bearer-token:}")
    private val staticFallbackToken: String
) : HttpHeadersProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cachedUserToken = AtomicReference<String?>(null)

    override fun getHeaders(instance: Instance): HttpHeaders {
        val headers = HttpHeaders()
        val token = currentUserToken() ?: cachedUserToken.get() ?: staticFallbackToken.takeIf { it.isNotBlank() }
        token?.let { headers.setBearerAuth(it) }
        return headers
    }

    private fun currentUserToken(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        return try {
            val client =
                authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
                    auth.authorizedClientRegistrationId,
                    auth.name
                ) ?: return null
            val tokenValue = client.accessToken.tokenValue
            cachedUserToken.set(tokenValue)
            tokenValue
        } catch (ex: Exception) {
            log.warn("failed to load OAuth2 access token for {}: {}", auth.name, ex.message)
            null
        }
    }
}