package com.beancounter.marketdata.providers.sec

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.stereotype.Service

/**
 * Rate-limited wrapper around [SecGateway].
 *
 * The `sec` limiter in `application.yml` paces calls at 1 per 150ms, inside SEC's published
 * fair-use ceiling of 10 requests/second. Exceeding it earns an HTTP 403 for the source IP.
 */
@Service
class SecProxy(
    private val secGateway: SecGateway
) {
    @RateLimiter(name = "sec")
    fun getCompanyTickers(): String = secGateway.getCompanyTickers()

    @RateLimiter(name = "sec")
    fun getSubmissions(cik10: String): String = secGateway.getSubmissions(cik10)

    @RateLimiter(name = "sec")
    fun getCompanyFacts(cik10: String): String = secGateway.getCompanyFacts(cik10)
}