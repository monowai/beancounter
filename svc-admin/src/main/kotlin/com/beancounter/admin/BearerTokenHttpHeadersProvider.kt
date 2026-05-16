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
import java.time.Instant
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
 *     request — used by background polling threads. Cleared once expired so
 *     it does not block the static fallback.
 *  3. An optional static token from `BC_ADMIN_M2M_TOKEN` — final fallback
 *     when no admin has logged in this boot and any cached token has aged out.
 */
@Component
class BearerTokenHttpHeadersProvider(
    private val authorizedClientService: OAuth2AuthorizedClientService,
    @Value("\${beancounter.admin.client.bearer-token:}")
    private val staticFallbackToken: String
) : HttpHeadersProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cachedUserToken = AtomicReference<CachedToken?>(null)

    override fun getHeaders(instance: Instance): HttpHeaders {
        val headers = HttpHeaders()
        val token = currentUserToken() ?: validCachedToken() ?: staticFallbackToken.takeIf { it.isNotBlank() }
        token?.let { headers.setBearerAuth(it) }
        return headers
    }

    private fun validCachedToken(): String? {
        val cached = cachedUserToken.get() ?: return null
        if (cached.expiresAt != null && !cached.expiresAt.isAfter(Instant.now())) {
            cachedUserToken.compareAndSet(cached, null)
            return null
        }
        return cached.value
    }

    private fun currentUserToken(): String? {
        val auth = SecurityContextHolder.getContext().authentication as? OAuth2AuthenticationToken ?: return null
        return try {
            val client =
                authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
                    auth.authorizedClientRegistrationId,
                    auth.name
                ) ?: return null
            val accessToken = client.accessToken
            cachedUserToken.set(CachedToken(accessToken.tokenValue, accessToken.expiresAt))
            accessToken.tokenValue
        } catch (ex: Exception) {
            log.warn("failed to load OAuth2 access token for {}: {}", auth.name, ex.message)
            null
        }
    }

    private data class CachedToken(
        val value: String,
        val expiresAt: Instant?
    )
}