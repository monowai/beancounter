package com.beancounter.marketdata.apikey

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

/**
 * Fixed-window, in-memory rate limiter for the (unauthenticated) token
 * exchange endpoint, keyed by client address ([clientKey]) - the only
 * signal available before [ApiKeyService.verify] has run. Single-JVM only:
 * a multi-instance deployment would need a shared store, out of scope for
 * phase 2.
 *
 * `maxRequests`/`window`/`trustedProxies` are constructor params (not read
 * once into a `val` from `@Value` defaults only) so tests can tighten the
 * window or widen the trust list without needing a whole new Spring context.
 *
 * `trustedProxies` is a regex over the whole peer address, the same shape as
 * Tomcat's `RemoteIpValve.internalProxies`. The default covers loopback,
 * RFC1918, link-local and IPv6 loopback - i.e. the in-cluster hops (ingress,
 * the mobile BFF). On kauri the Cloudflare edge addresses must be added to
 * `auth.bc-issuer.rate-limit.trusted-proxies` or every user behind one edge
 * node shares a single window (see [clientKey]).
 */
@Component
class TokenRateLimiter(
    @Value($$"${auth.bc-issuer.rate-limit.max-requests:10}") private val maxRequests: Int = 10,
    @Value($$"${auth.bc-issuer.rate-limit.window:PT1M}") private val window: Duration = Duration.ofMinutes(1),
    @Value($$"${auth.bc-issuer.rate-limit.trusted-proxies:$$DEFAULT_TRUSTED_PROXIES}")
    private val trustedProxies: String = DEFAULT_TRUSTED_PROXIES
) {
    private data class Window(
        val start: Instant,
        val count: Int
    )

    private val windows = ConcurrentHashMap<String, Window>()

    /** Client windows currently tracked - observable surface for the pruning behaviour. */
    internal val trackedClients: Int get() = windows.size

    /** @throws TooManyRequestsException once [clientId] exceeds [maxRequests] within [window]. */
    fun check(clientId: String) {
        val now = Instant.now()
        // Prune expired windows on every call so the map stays bounded by
        // active callers - without this, every distinct IP ever seen would
        // accumulate forever on an unauthenticated endpoint.
        windows.entries.removeIf { Duration.between(it.value.start, now) >= window }
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

    // Compiled once; a malformed operator value must fail bean construction
    // with a message naming the property, not a bare PatternSyntaxException.
    private val trustedProxy =
        try {
            Regex(trustedProxies)
        } catch (e: PatternSyntaxException) {
            throw IllegalArgumentException(
                "Invalid auth.bc-issuer.rate-limit.trusted-proxies regex: $trustedProxies",
                e
            )
        }

    /**
     * Rate-limit key for [request].
     *
     * The socket peer (`remoteAddr`) is the only address nobody can forge.
     * If the peer is **not** a trusted proxy, `X-Forwarded-For` is ignored
     * outright and the peer is the key - a direct caller can never influence
     * its own bucket by writing the header (#1174 security review).
     *
     * If the peer is trusted, walk the header from the **right** (the hop
     * nearest us) and return the first address that is not itself a trusted
     * proxy - that is the client as seen by the outermost proxy we trust;
     * anything to its left was supplied by that client and is untrusted.
     * If every hop is trusted (or the header is absent) fall back to the
     * leftmost address, else the peer.
     *
     * Behind a reverse proxy or the mobile BFF every caller shares one
     * `remoteAddr`, so keying on it alone collapses all clients into a
     * single window (#1173) - the trust list is what lets them be told apart.
     */
    fun clientKey(request: HttpServletRequest): String {
        val peer = request.remoteAddr
        if (!trustedProxy.matches(peer)) return peer
        val hops =
            request
                .getHeader(FORWARDED_FOR_HEADER)
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
        return hops.lastOrNull { !trustedProxy.matches(it) }
            ?: hops.firstOrNull()
            ?: peer
    }

    companion object {
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
        const val DEFAULT_TRUSTED_PROXIES =
            """10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|""" +
                """127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2[0-9]|3[0-1])\.\d{1,3}\.\d{1,3}|""" +
                """0:0:0:0:0:0:0:1|::1"""
    }
}