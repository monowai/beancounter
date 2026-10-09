package com.beancounter.marketdata.apikey

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Duration

/**
 * Unit coverage for [TokenRateLimiter]'s window bookkeeping - the limit
 * itself (429 once exhausted) is covered end-to-end in
 * [ApiKeyTokenControllerTest].
 */
internal class TokenRateLimiterTest {
    @Test
    fun `prunes expired windows so the map stays bounded by active callers`() {
        val limiter = TokenRateLimiter(maxRequests = 5, window = Duration.ofMillis(200))
        limiter.check("ip-1")
        limiter.check("ip-2")
        assertThat(limiter.trackedClients).isEqualTo(2)

        Thread.sleep(300)
        limiter.check("ip-3")

        assertThat(limiter.trackedClients).isEqualTo(1)
    }

    @Test
    fun `a caller gets a fresh window once the previous one expires`() {
        val limiter = TokenRateLimiter(maxRequests = 2, window = Duration.ofMillis(200))
        limiter.check("ip-1")
        limiter.check("ip-1")
        assertThatThrownBy { limiter.check("ip-1") }
            .isInstanceOf(TooManyRequestsException::class.java)

        Thread.sleep(300)
        limiter.check("ip-1")
        assertThat(limiter.trackedClients).isEqualTo(1)
    }

    private fun requestFrom(
        peer: String,
        forwardedFor: String = ""
    ): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = peer
            if (forwardedFor.isNotBlank()) addHeader("X-Forwarded-For", forwardedFor)
        }

    @Test
    fun `should use leftmost address when forwarded header lists only trusted proxies after it`() {
        val request = requestFrom("10.0.0.1", " 203.0.113.9 , 10.0.0.1")

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("203.0.113.9")
    }

    @Test
    fun `should ignore forwarded header when peer is not a trusted proxy`() {
        val request = requestFrom("198.51.100.7", "203.0.113.9")

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("198.51.100.7")
    }

    @Test
    fun `should use rightmost untrusted address when trusted proxies are appended`() {
        val request = requestFrom("127.0.0.1", "203.0.113.9, 198.51.100.4, 10.0.0.1")

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("198.51.100.4")
    }

    @Test
    fun `should use leftmost address when every forwarded hop is trusted`() {
        val request = requestFrom("::1", "10.1.1.1, 10.2.2.2")

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("10.1.1.1")
    }

    @Test
    fun `should honour forwarded header from a peer matched by the configured trusted proxies`() {
        val edgeTrusted = TokenRateLimiter(trustedProxies = """203\.0\.113\.\d{1,3}""")
        val request = requestFrom("203.0.113.50", "198.51.100.4, 203.0.113.51")

        assertThat(edgeTrusted.clientKey(request)).isEqualTo("198.51.100.4")
        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("203.0.113.50")
    }

    @Test
    fun `should fall back to remote address without forwarded header`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("10.0.0.1")
    }

    @Test
    fun `should fall back to remote address when forwarded header is blank`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", " , ")

        assertThat(TokenRateLimiter().clientKey(request)).isEqualTo("10.0.0.1")
    }
}