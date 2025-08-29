package com.beancounter.auth.client

import com.beancounter.auth.model.OpenIdResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * JWT Token Cache Service with expiry-aware caching and automatic refresh.
 *
 * This service provides:
 * - Intelligent token caching with expiry awareness
 * - Automatic token refresh on expiry
 * - Proactive token refresh (48 hours or configurable)
 * - Thread-safe operations
 */
@Service
class JwtTokenCacheService(
    private val jwtDecoder: JwtDecoder,
    @param:Value("\${auth.jwt.proactive-refresh-seconds:172800}") // Default: 48 hours = 172800 seconds
    private val proactiveRefreshSeconds: Long
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    /**
     * Cached token with expiry information
     */
    private data class CachedToken(
        val response: OpenIdResponse,
        val expiresAt: Instant,
        val proactiveRefreshAt: Instant
    )

    /**
     * Get a valid JWT token, refreshing if necessary.
     *
     * @param cacheKey The cache key (typically client credentials)
     * @param tokenSupplier Function to obtain a fresh token
     * @return Valid OpenIdResponse
     */
    fun getValidToken(
        cacheKey: String,
        tokenSupplier: () -> OpenIdResponse
    ): OpenIdResponse {
        val cached = tokenCache[cacheKey]
        val now = Instant.now()

        // Check if we need to refresh the token
        return when {
            cached == null -> {
                log.debug("No cached token found for key: $cacheKey")
                refreshAndCacheToken(cacheKey, tokenSupplier)
            }
            now.isAfter(cached.expiresAt) -> {
                log.info("Token expired for key: $cacheKey, refreshing...")
                refreshAndCacheToken(cacheKey, tokenSupplier)
            }
            now.isAfter(cached.proactiveRefreshAt) -> {
                log.info("Token approaching expiry for key: $cacheKey, proactively refreshing...")
                refreshAndCacheToken(cacheKey, tokenSupplier)
            }
            !isTokenValid(cached.response.token) -> {
                log.warn("Cached token is invalid (possibly expired), refreshing for key: $cacheKey")
                refreshAndCacheToken(cacheKey, tokenSupplier)
            }
            else -> {
                log.trace("Using cached valid token for key: $cacheKey")
                cached.response
            }
        }
    }

    /**
     * Validate token by attempting to decode it
     */
    private fun isTokenValid(token: String): Boolean =
        try {
            val jwt = jwtDecoder.decode(token)
            val now = Instant.now()
            jwt.expiresAt?.isAfter(now) ?: false
        } catch (e: JwtException) {
            log.debug("Token validation failed: ${e.message}")
            false
        }

    /**
     * Refresh and cache a new token
     */
    private fun refreshAndCacheToken(
        cacheKey: String,
        tokenSupplier: () -> OpenIdResponse
    ): OpenIdResponse =
        try {
            val response = tokenSupplier()
            val now = Instant.now()

            // Calculate expiry times
            val expiresAt = now.plusSeconds(response.expiry)
            val proactiveRefreshAt =
                now.plusSeconds(
                    minOf(response.expiry - proactiveRefreshSeconds, response.expiry / 2)
                )

            // Cache the token
            tokenCache[cacheKey] =
                CachedToken(
                    response = response,
                    expiresAt = expiresAt,
                    proactiveRefreshAt = proactiveRefreshAt
                )

            log.info(
                "Token cached for key: $cacheKey, expires at: $expiresAt, proactive refresh at: $proactiveRefreshAt"
            )
            response
        } catch (e: Exception) {
            log.error("Failed to refresh token for key: $cacheKey", e)
            throw e
        }

    /**
     * Clear cached token for a specific key
     */
    fun clearToken(cacheKey: String) {
        tokenCache.remove(cacheKey)
        log.debug("Cleared cached token for key: $cacheKey")
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): Map<String, Any> {
        val now = Instant.now()
        return mapOf(
            "totalCachedTokens" to tokenCache.size,
            "validTokens" to tokenCache.values.count { now.isBefore(it.expiresAt) },
            "expiredTokens" to tokenCache.values.count { now.isAfter(it.expiresAt) },
            "tokensNeedingProactiveRefresh" to
                tokenCache.values.count {
                    now.isAfter(it.proactiveRefreshAt) && now.isBefore(it.expiresAt)
                }
        )
    }
}