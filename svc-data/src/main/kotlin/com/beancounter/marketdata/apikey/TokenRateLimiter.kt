package com.beancounter.marketdata.apikey

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fixed-window, in-memory rate limiter for the (unauthenticated) token
 * exchange endpoint, keyed by caller IP - the only signal available before
 * [ApiKeyService.verify] has run. Single-JVM only: a multi-instance
 * deployment would need a shared store, out of scope for phase 2.
 *
 * `maxRequests`/`window` are constructor params (not read once into a
 * `val` from `@Value` defaults only) so tests can tighten the window
 * without needing a whole new Spring context.
 */
@Component
class TokenRateLimiter(
    @Value($$"${auth.bc-issuer.rate-limit.max-requests:10}") private val maxRequests: Int = 10,
    @Value($$"${auth.bc-issuer.rate-limit.window:PT1M}") private val window: Duration = Duration.ofMinutes(1)
) {
    private data class Window(
        val start: Instant,
        val count: Int
    )

    private val windows = ConcurrentHashMap<String, Window>()

    /** @throws TooManyRequestsException once [clientId] exceeds [maxRequests] within [window]. */
    fun check(clientId: String) {
        val now = Instant.now()
        val current =
            windows.compute(clientId) { _, existing ->
                if (existing == null || Duration.between(existing.start, now) >= window) {
                    Window(now, 1)
                } else {
                    Window(existing.start, existing.count + 1)
                }
            }
        if (current != null && current.count > maxRequests) {
            throw TooManyRequestsException("Rate limit exceeded for token exchange")
        }
    }
}