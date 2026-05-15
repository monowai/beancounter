package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.stereotype.Service

/**
 * Rate-limited wrapper around [EodhdGateway].
 *
 * The `eodhd` rate limiter is configured in `application.yml` to match the active EODHD plan.
 * Defaults are sized for the $19.99 EOD Historical Data tier (1000 req/min).
 */
@Service
class EodhdProxy(
    private val eodhdGateway: EodhdGateway
) {
    @RateLimiter(name = "eodhd")
    fun getPrice(
        symbol: String,
        date: String,
        apiKey: String
    ): List<EodhdPrice> =
        eodhdGateway.getPrice(
            symbol,
            date,
            apiKey
        )

    @RateLimiter(name = "eodhd")
    fun getHistory(
        symbol: String,
        dateFrom: String,
        dateTo: String,
        apiKey: String
    ): List<EodhdPrice> =
        eodhdGateway.getHistory(
            symbol,
            dateFrom,
            dateTo,
            apiKey
        )
}