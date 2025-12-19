package com.beancounter.marketdata.providers.morningstar

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Rate-limited proxy for Morningstar API calls.
 */
@Service
class MorningstarProxy {
    private lateinit var morningstarGateway: MorningstarGateway

    companion object {
        private val log = LoggerFactory.getLogger(MorningstarProxy::class.java)
    }

    @Autowired(required = false)
    fun setMorningstarGateway(gateway: MorningstarGateway) {
        this.morningstarGateway = gateway
    }

    /**
     * Fetches price data for a security.
     * Rate-limited to avoid being blocked by Morningstar.
     */
    @RateLimiter(name = "morningstar")
    fun getPrice(
        securityId: String,
        currencyId: String = "GBP",
        startDate: LocalDate = LocalDate.now().minusDays(7)
    ): String? {
        log.debug("Fetching price for {} from Morningstar", securityId)
        return morningstarGateway.getPrice(securityId, currencyId, startDate)
    }
}