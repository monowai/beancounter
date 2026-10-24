package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Optional
import java.util.concurrent.CompletableFuture

/**
 * Live (delayed) quotes for display, one provider call per provider per read, reused for
 * [TTL]. EODHD refreshes its live snapshot about once a minute, so a shorter reuse buys no
 * fresher price and only spends API calls. Misses are reused too, so an asset the provider
 * cannot quote is not asked for again on every read. Concurrent reads of the same asset share
 * the one in-flight fetch. Only markets in session are asked: outside trading hours the stored
 * close is as fresh as any quote. A failing provider counts as a miss: the caller falls back to
 * the stored close. Quotes are never persisted.
 */
@Service
class LiveQuoteService(
    private val mdFactory: MdFactory,
    private val clock: Clock = Clock.systemUTC(),
    private val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils())
) {
    private val cache: AsyncCache<String, Optional<MarketData>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_ASSETS)
            .ticker { clock.millis() * NANOS_PER_MILLI }
            .buildAsync()

    fun quotes(assets: Collection<Asset>): Map<String, MarketData> {
        val now = ZonedDateTime.now(clock)
        val trading =
            assets
                .filter { marketUtils.isInSession(now, it.market) }
                .associateBy { it.id }
        if (trading.isEmpty()) return emptyMap()
        return cache
            .getAll(trading.keys) { ids, _ ->
                CompletableFuture.completedFuture(load(ids.mapNotNull(trading::get)))
            }.join()
            .mapNotNull { (id, quote) -> quote.orElse(null)?.let { id to it } }
            .toMap()
    }

    /**
     * The live quote for each of [assets] where it is at least as recent as its [stored] close,
     * else the stored close. Read for the price now, not last night's close.
     */
    fun livePrices(
        assets: Collection<Asset>,
        stored: Map<String, MarketData>
    ): Map<String, MarketData> {
        val quotes = quotes(assets.distinctBy { it.id })
        return assets
            .mapNotNull { asset ->
                val close = stored[asset.id]
                val quote =
                    quotes[asset.id]?.takeIf {
                        it.close.signum() > 0 &&
                            (close == null || it.priceDate >= close.priceDate)
                    }
                (quote ?: close)?.let { asset.id to it }
            }.toMap()
    }

    private fun load(assets: List<Asset>): Map<String, Optional<MarketData>> {
        val fetched =
            assets
                .groupBy { mdFactory.getMarketDataProvider(it.market) }
                .flatMap { (provider, batch) -> fetch(provider, batch).entries }
                .associate { it.key to it.value }
        return assets.associate { it.id to Optional.ofNullable(fetched[it.id]) }
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