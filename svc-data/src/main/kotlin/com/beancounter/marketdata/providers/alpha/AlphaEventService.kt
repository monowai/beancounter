package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Obtains corporate actions since inception. Alpha stores these in price history.  This
 * service will drop anything that is not considered an event.
 */
@Service
class AlphaEventService(
    val alphaGateway: AlphaGateway,
    val alphaConfig: AlphaConfig,
) {
    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    @RateLimiter(name = "alphaVantage")
    // AV "Free Plan" rate limits
    @Cacheable("alpha.asset.event")
    fun getEvents(asset: Asset): PriceResponse {
        val json =
            alphaGateway.getAdjusted(
                asset.code,
                apiKey,
            )
        if (json.contains("Error Message")) {
            log.error("Provider API error $json")
            return PriceResponse()
        }
        val priceResponse: PriceResponse =
            alphaConfig
                .getObjectMapper()
                .readValue(
                    json,
                    PriceResponse::class.java,
                )
        val events = priceResponse.data.filter(this::inFilter)
        return PriceResponse(events)
    }

    private fun inFilter(marketData: MarketData): Boolean = (isSplit(marketData) || isDividend(marketData))

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
