package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.Optional

/**
 * Live (delayed) quotes for display, one provider call per provider per read, reused for
 * [TTL]. EODHD refreshes its live snapshot about once a minute, so a shorter reuse buys no
 * fresher price and only spends API calls. Misses are reused too, so an asset the provider
 * cannot quote is not asked for again on every read. A failing provider counts as a miss: the
 * caller falls back to the stored close. Quotes are never persisted.
 */
@Service
class LiveQuoteService(
    private val mdFactory: MdFactory,
    clock: Clock = Clock.systemUTC()
) {
    private val cache: Cache<String, Optional<MarketData>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ASSETS)
            .ticker { clock.millis() * NANOS_PER_MILLI }
            .build()

    fun quotes(assets: Collection<Asset>): Map<String, MarketData> {
        if (assets.isEmpty()) return emptyMap()
        assets
            .filter { cache.getIfPresent(it.id) == null }
            .groupBy { mdFactory.getMarketDataProvider(it.market) }
            .forEach { (provider, batch) ->
                val fetched = fetch(provider, batch)
                batch.forEach { cache.put(it.id, Optional.ofNullable(fetched[it.id])) }
            }
        return assets
            .mapNotNull { asset -> cache.getIfPresent(asset.id)?.orElse(null)?.let { asset.id to it } }
            .toMap()
    }

    private fun fetch(
        provider: MarketDataPriceProvider,
        batch: List<Asset>
    ): Map<String, MarketData> =
        try {
            provider.getQuotes(batch)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            log.debug("Live quotes unavailable from {}: {}", provider.getId(), e.message)
            emptyMap()
        }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(60)
        const val MAX_ASSETS = 10_000L
        const val NANOS_PER_MILLI = 1_000_000L
        val log = LoggerFactory.getLogger(LiveQuoteService::class.java)
    }
}