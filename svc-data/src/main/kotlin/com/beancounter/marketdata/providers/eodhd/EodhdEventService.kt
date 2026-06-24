package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.MarketDataRepo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.math.BigDecimal

/**
 * Obtains corporate actions (splits + dividends) for assets routed through the EODHD provider.
 *
 * EODHD exposes splits and dividends via separate endpoints (`/api/splits` and `/api/div`), unlike
 * AlphaVantage which embeds both in `TIME_SERIES_DAILY_ADJUSTED`. This service stitches both streams
 * into a single [PriceResponse] of event-bearing [MarketData] rows so the rest of BeanCounter can stay
 * provider-agnostic.
 *
 * Routing follows the same `beancounter.market.providers.eodhd.markets` allowlist as
 * [EodhdPriceService]: whichever provider owns prices for a market also owns its events. Markets not
 * in the allowlist fall back to repository-cached events (`marketDataRepo.findEventsByAssetId`) so
 * unrelated markets still answer cleanly.
 */
@Service
class EodhdEventService(
    private val assetFinder: AssetFinder,
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val marketDataRepo: MarketDataRepo
) {
    // Cache-aware self-invocation: getEvents must call fetchProviderEvents through the Spring
    // proxy or @Cacheable is bypassed. Defaults to `this` for plain unit tests; Spring injects
    // the @Lazy proxy at startup (the setter), breaking the construction cycle.
    @set:Autowired
    @set:Lazy
    internal var self: EodhdEventService = this

    fun getEvents(assetId: String): PriceResponse = getEvents(assetFinder.find(assetId))

    fun getEvents(asset: Asset): PriceResponse {
        if (!isMarketSupported(asset.market.code)) {
            log.debug("Market {} not supported by EODHD; falling back to repo for {}", asset.market.code, asset.code)
            return repoFallback(asset)
        }
        return try {
            self.fetchProviderEvents(asset)
        } catch (e: RestClientException) {
            // A provider I/O blip must not 500 the events endpoint — bc-event consumes it on
            // a RabbitMQ listener, and a 500 exhausts the retry policy and drops the message
            // (EVENT-1F). Degrade to stored events — and crucially do NOT cache this fallback,
            // so a brief outage can't pin stale events for the cache TTL.
            log.warn("EODHD events fetch failed for {} — falling back to stored events: {}", asset.code, e.message)
            repoFallback(asset)
        }
    }

    /**
     * Provider fetch — cached on success only. The [RestClientException] on a provider I/O
     * error propagates out (caches never store exceptions), so the caller's repo fallback is
     * never cached under this key.
     */
    @Cacheable("eodhd.asset.event", key = "#asset.id")
    fun fetchProviderEvents(asset: Asset): PriceResponse {
        val symbol = eodhdConfig.getPriceCode(asset)
        val divs = eodhdProxy.getDividends(symbol, eodhdConfig.apiKey).map { toDividendRow(asset, it) }
        val splits = eodhdProxy.getSplits(symbol, eodhdConfig.apiKey).map { toSplitRow(asset, it) }
        return PriceResponse(divs + splits)
    }

    private fun repoFallback(asset: Asset): PriceResponse = PriceResponse(marketDataRepo.findEventsByAssetId(asset.id))

    private fun isMarketSupported(marketCode: String): Boolean {
        val supported = eodhdConfig.markets ?: return false
        return supported.contains(marketCode)
    }

    private fun toDividendRow(
        asset: Asset,
        dividend: com.beancounter.marketdata.providers.eodhd.model.EodhdDividend
    ): MarketData {
        val recordDate = dividend.recordDate ?: dividend.date
        return MarketData(
            asset = asset,
            priceDate = recordDate,
            source = EodhdPriceService.ID
        ).also {
            it.dividend = dividend.value
            it.split = BigDecimal.ONE
        }
    }

    private fun toSplitRow(
        asset: Asset,
        split: com.beancounter.marketdata.providers.eodhd.model.EodhdSplit
    ): MarketData =
        MarketData(
            asset = asset,
            priceDate = split.date,
            source = EodhdPriceService.ID
        ).also {
            it.dividend = BigDecimal.ZERO
            it.split = split.factor()
        }

    companion object {
        private val log = LoggerFactory.getLogger(EodhdEventService::class.java)
    }
}