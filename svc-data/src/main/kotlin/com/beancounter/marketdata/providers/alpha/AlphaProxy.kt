package com.beancounter.marketdata.providers.alpha

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Resilience wrapper around the gateway.
 */
@Service
class AlphaProxy {
    private lateinit var alphaGateway: AlphaGateway

    @Autowired(required = false)
    fun setAlphaGateway(alphaGateway: AlphaGateway) {
        this.alphaGateway = alphaGateway
    }

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun getCurrent(code: String, apiKey: String): String? {
        return alphaGateway.getCurrent(code, apiKey)
    }

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun getHistoric(code: String, apiKey: String): String? {
        return alphaGateway.getHistoric(code, apiKey)
    }

    @RateLimiter(name = "alphaVantage") // AV "Free Plan" rate limits
    fun search(symbol: String, apiKey: String): String {
        return alphaGateway.search(symbol, apiKey)
    }

    fun adjusted(code: String, apiKey: String): String? {
        return alphaGateway.getAdjusted(code, apiKey)
    }
}
