package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Live (delayed) quotes for display, one provider call per provider per read, reused for
 * [TTL]. EODHD refreshes its live snapshot about once a minute, so a shorter reuse buys no
 * fresher price and only spends API calls. Misses are reused too, so an asset the provider
 * cannot quote is not asked for again on every read. Quotes are never persisted.
 */
@Service
class LiveQuoteService(
    private val mdFactory: MdFactory,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Cached(
        val quote: MarketData?,
        val at: Instant
    )

    private val cache = ConcurrentHashMap<String, Cached>()

    fun quotes(assets: Collection<Asset>): Map<String, MarketData> {
        if (assets.isEmpty()) return emptyMap()
        val now = clock.instant()
        val stale = assets.filter { asset -> cache[asset.id]?.let { Duration.between(it.at, now) > TTL } ?: true }
        stale
            .groupBy { mdFactory.getMarketDataProvider(it.market) }
            .forEach { (provider, batch) ->
                val fetched = provider.getQuotes(batch)
                batch.forEach { cache[it.id] = Cached(fetched[it.id], now) }
            }
        return assets.mapNotNull { asset -> cache[asset.id]?.quote?.let { asset.id to it } }.toMap()
    }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(60)
    }
}