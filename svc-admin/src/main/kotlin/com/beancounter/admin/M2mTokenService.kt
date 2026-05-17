package com.beancounter.admin

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Fetches and caches an Auth0 M2M access token for background scrape
 * threads in [BearerTokenHttpHeadersProvider]. SBA's monitor schedule
 * runs outside any user request, so the OIDC pass-through path has
 * nothing to read. Without a working M2M fallback, scrapes against
 * actuator endpoints get 401, the actuator discovery returns the
 * health endpoint only, and the SBA UI shows no metrics.
 *
 * Uses the same M2M client (AUTH0_SERVICE_ID / AUTH0_SERVICE_SECRET)
 * that bc-data, bc-event etc. already use for inter-service calls —
 * the issued token carries `beancounter:system`, which jar-auth's
 * actuator filter accepts alongside `beancounter:admin`.
 *
 * Tokens are cached until 30s before their `exp`. A failed exchange
 * returns null and is retried on the next request (no negative cache).
 */
@Service
class M2mTokenService(
    @Value("\${auth.uri:}")
    private val authIssuerUri: String,
    @Value("\${auth.audience:https://holdsworth.app}")
    private val audience: String,
    @Value("\${auth0.service.id:\${AUTH0_SERVICE_ID:}}")
    private val clientId: String,
    @Value("\${auth0.service.secret:\${AUTH0_SERVICE_SECRET:}}")
    private val clientSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    private val cached = AtomicReference<CachedM2m?>(null)
    private val refreshSkewSeconds = 30L

    fun token(): String? {
        cached.get()?.let { existing ->
            if (Instant.now().isBefore(existing.expiresAt.minusSeconds(refreshSkewSeconds))) {
                return existing.value
            }
        }
        if (clientId.isBlank() || clientSecret.isBlank() || authIssuerUri.isBlank()) {
            return null
        }
        return fetch()?.also { cached.set(it) }?.value
    }

    private fun fetch(): CachedM2m? {
        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", clientId)
                add("client_secret", clientSecret)
                add("audience", audience)
            }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val tokenUrl = "${authIssuerUri.trimEnd('/')}/oauth/token"
        return try {
            val response =
                restTemplate.postForObject(
                    tokenUrl,
                    HttpEntity(formData, headers),
                    Auth0TokenResponse::class.java
                )
            response?.access_token?.let { CachedM2m(it, Instant.now().plusSeconds(response.expires_in)) }
        } catch (ex: RestClientException) {
            log.warn("M2M token fetch failed at {}: {}", tokenUrl, ex.message)
            null
        }
    }

    private data class CachedM2m(
        val value: String,
        val expiresAt: Instant
    )

    @Suppress("PropertyName", "ConstructorParameterNaming")
    private data class Auth0TokenResponse(
        val access_token: String? = null,
        val token_type: String? = null,
        val expires_in: Long = 0
    )
}