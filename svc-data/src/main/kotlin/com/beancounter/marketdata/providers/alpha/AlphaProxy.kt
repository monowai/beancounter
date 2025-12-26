package com.beancounter.marketdata.providers.alpha

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.stereotype.Service

/**
 * Resilience wrapper around the gateway.
 */
@Service
class AlphaProxy(
    private val alphaGateway: AlphaGateway
) {
    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun getCurrent(
        code: String,
        apiKey: String
    ): String =
        alphaGateway.getCurrent(
            code,
            apiKey
        )

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun getHistoric(
        code: String,
        apiKey: String
    ): String =
        alphaGateway.getHistoric(
            code,
            apiKey
        )

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun search(
        symbol: String,
        apiKey: String
    ): String =
        alphaGateway.search(
            symbol,
            apiKey
        )

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun getAdjusted(
        code: String,
        apiKey: String
    ): String =
        alphaGateway.getAdjusted(
            code,
            apiKey
        )

    @RateLimiter(name = "alphaVantage")
    fun getOverview(
        symbol: String,
        apiKey: String
    ): String =
        alphaGateway.getOverview(
            symbol,
            apiKey
        )

    @RateLimiter(name = "alphaVantage")
    fun getEtfProfile(
        symbol: String,
        apiKey: String
    ): String =
        alphaGateway.getEtfProfile(
            symbol,
            apiKey
        )
}