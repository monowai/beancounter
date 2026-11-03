package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate

/**
 * Stored price history read for charts and valuations, rebased onto the latest share basis.
 * DB only for the bulk read; the single-asset history also consults the provider's splits.
 */
@Service
class PriceHistoryReader(
    private val marketDataRepo: MarketDataRepo,
    private val assetFinder: AssetFinder,
    private val priceSplits: PriceSplits,
    priceProviders: List<MarketDataPriceProvider> = emptyList()
) {
    private val log = LoggerFactory.getLogger(PriceHistoryReader::class.java)

    /**
     * Provider IDs that persist already-adjusted close prices, handed to [SplitAdjuster]
     * to suppress double-adjustment.
     */
    private val adjustedSources: Set<String> =
        priceProviders
            .filter { it.shipsAdjustedClose() }
            .map { it.getId() }
            .toSet()

    init {
        if (adjustedSources.isNotEmpty()) {
            log.info("SplitAdjuster will skip already-adjusted sources: {}", adjustedSources)
        }
    }

    /**
     * Get market data for multiple assets across multiple dates (DB-only, no provider calls).
     * For dates without exact matches (weekends/holidays), falls back to the nearest prior price.
     * Returns a map keyed by date string.
     *
     * Implementation note: a single window query loads every row for the requested assets
     * between [min(dates) - FALLBACK_LOOKBACK_DAYS] and [max(dates)]. Exact + nearest-prior
     * resolution then runs in memory. The prior implementation issued a per-(asset, date)
     * `findTop1...` fallback query for every weekend/holiday slot, which timed out under
     * the wealth-performance "ALL" chart load (assets × ~50 monthly dates → hundreds of DB
     * roundtrips, > 30s read-timeout on bc-position → /api/prices/bulk).
     */
    @Transactional
    fun getBulkMarketData(
        assets: Collection<Asset>,
        dates: Collection<LocalDate>
    ): Map<String, List<MarketData>> {
        if (assets.isEmpty() || dates.isEmpty()) return emptyMap()

        val started = System.nanoTime()
        val minDate = dates.min()
        val maxDate = dates.max()
        val window =
            marketDataRepo.findByAssetInAndPriceDateBetween(
                assets,
                minDate.minusDays(FALLBACK_LOOKBACK_DAYS),
                maxDate
            )
        // Per-asset chronological list — already ascending from the query's ORDER BY.
        // Rebase onto the latest post-split basis before resolution so wealth / TWR
        // charts don't span split dates with raw pre-split closes (the DB stores raw
        // closes for any provider that doesn't carry split coefficients on each row,
        // e.g. Alpha TIME_SERIES_DAILY).
        val byAsset =
            window
                .groupBy { it.asset.id }
                .mapValues { (_, series) -> adjustSeriesForSplits(series) }
                .toMutableMap()

        topUpMissingPrivateAssets(assets, byAsset, maxDate)

        val result = mutableMapOf<String, List<MarketData>>()
        for (date in dates) {
            val mdList = mutableListOf<MarketData>()
            for (asset in assets) {
                val series = byAsset[asset.id] ?: continue
                val resolved = nearestOnOrBefore(series, date)
                if (resolved != null) mdList.add(resolved)
            }
            result[date.toString()] = mdList
        }
        logBulkTiming(assets.size, dates.size, window.size, started)
        return result
    }

    // Split adjustment can call the event provider per asset, so a cold bulk read is slow
    // with nothing else logged. WARN when slow so the next stall shows in the pod log.
    private fun logBulkTiming(
        assets: Int,
        dates: Int,
        rows: Int,
        started: Long
    ) {
        val elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis()
        if (elapsedMs >= SLOW_BULK_MS) {
            log.warn("Slow bulk prices: {}ms for {} assets x {} dates ({} rows)", elapsedMs, assets, dates, rows)
        } else {
            log.debug("Bulk prices: {}ms for {} assets x {} dates ({} rows)", elapsedMs, assets, dates, rows)
        }
    }

    /**
     * PRIVATE-market assets (user-entered prices for real estate, art, etc.) can go
     * months between updates — the FALLBACK_LOOKBACK_DAYS window that suits listed
     * markets is far too narrow to reach their single price row. Top up any PRIVATE
     * asset that came back empty from the windowed load with its single latest
     * on-or-before-maxDate row, so the existing [nearestOnOrBefore] resolution below
     * picks it up for every requested date instead of silently omitting the asset
     * (which pushed svc-position back onto its purchase-cost fallback, see #1045).
     * One extra query per missing PRIVATE asset — they are rare (typically 1-2 per
     * user) so this doesn't reintroduce the N+1 pattern the window query replaced.
     */
    private fun topUpMissingPrivateAssets(
        assets: Collection<Asset>,
        byAsset: MutableMap<String, List<MarketData>>,
        maxDate: LocalDate
    ) {
        assets
            .asSequence()
            .filter { it.market.code == PrivateMarketDataProvider.ID && byAsset[it.id].isNullOrEmpty() }
            .forEach { asset ->
                marketDataRepo
                    .findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(asset, maxDate)
                    .ifPresent { latest -> byAsset[asset.id] = listOf(latest) }
            }
    }

    /**
     * Apply [SplitAdjuster] to a chronological [MarketData] series and write the
     * adjusted OHLC back into the returned rows, so the bulk read path that feeds
     * `svc-position` performance charts sees a rebased basis.
     *
     * Splits come only from the `split` column on the stored rows, never from the
     * event provider: the bulk read is DB-only. Asking the provider per asset made a
     * cold wealth page fire two EODHD calls per asset inside this transaction and
     * stall past svc-position's 60s read timeout (kauri, 2026-10-03). A split the
     * database never stamped stays unadjusted here until `repairSplits` stamps it.
     */
    private fun adjustSeriesForSplits(series: List<MarketData>): List<MarketData> {
        if (series.size < 2) return series
        val adjusted = SplitAdjuster.adjust(series.map(PricePoint::from), adjustedSources = adjustedSources)
        if (adjusted === series) return series
        return series.zip(adjusted).map { (md, point) ->
            val unchanged =
                listOf(
                    point.close to md.close,
                    point.open to md.open,
                    point.high to md.high,
                    point.low to md.low
                ).all { (rebased, stored) -> rebased.compareTo(stored) == 0 }
            if (unchanged) {
                md
            } else {
                MarketData(
                    asset = md.asset,
                    priceDate = md.priceDate,
                    open = point.open,
                    close = point.close,
                    source = md.source
                ).also {
                    it.high = point.high
                    it.low = point.low
                    it.previousClose = point.previousClose
                    it.change = md.change
                    it.changePercent = md.changePercent
                    it.volume = md.volume
                    it.split = point.split
                    it.dividend = md.dividend
                }
            }
        }
    }

    /**
     * Find the latest entry with `priceDate <= target` in an ascending-sorted series.
     * Linear scan from the end — series is typically short (the FALLBACK_LOOKBACK_DAYS
     * window keeps it bounded) so a binary-search complication isn't worth it.
     */
    private fun nearestOnOrBefore(
        seriesAsc: List<MarketData>,
        target: LocalDate
    ): MarketData? {
        for (i in seriesAsc.indices.reversed()) {
            val md = seriesAsc[i]
            if (!md.priceDate.isAfter(target)) return md
        }
        return null
    }

    /**
     * Get the price history for an asset between two dates.
     * Returns the hydrated asset plus a chronological list of prices
     * (no repeated asset per row).
     */
    @Transactional
    fun getPriceHistory(
        assetId: String,
        from: LocalDate,
        to: LocalDate
    ): PriceHistoryResponse {
        val asset = assetFinder.find(assetId)
        val rawPrices =
            marketDataRepo
                .findPriceHistory(assetId, from, to)
                .map(PricePoint::from)
        val splitEvents = priceSplits.collectSplitEvents(asset, from, to)
        return PriceHistoryResponse(
            asset = asset,
            prices = SplitAdjuster.adjust(rawPrices, splitEvents, adjustedSources)
        )
    }

    private companion object {
        // How far before the earliest requested date to extend the bulk window
        // load. Covers long weekends + bank-holiday clusters so the in-memory
        // nearest-prior fallback has something to resolve to. 10 days is plenty
        // for any developed market.
        const val FALLBACK_LOOKBACK_DAYS = 10L
        const val SLOW_BULK_MS = 5_000L
    }
}