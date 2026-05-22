package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.PricePoint
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.TestEnvironmentUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * Persist prices obtained from providers and detect if Corporate Events need to be dispatched.
 */
@Service
class PriceService(
    private val marketDataRepo: MarketDataRepo,
    private val cashUtils: CashUtils,
    private val assetFinder: AssetFinder
) {
    private val log = LoggerFactory.getLogger(PriceService::class.java)
    private var eventProducer: EventProducer? = null
    private var cacheInvalidationProducer: CacheInvalidationProducer? = null
    private var alphaEventService: AlphaEventService? = null

    @Autowired(required = false)
    fun setAlphaEventService(alphaEventService: AlphaEventService?) {
        this.alphaEventService = alphaEventService
    }

    @Autowired(required = false)
    fun setEventWriter(eventProducer: EventProducer?) {
        this.eventProducer = eventProducer
    }

    @Autowired(required = false)
    fun setCacheInvalidationProducer(producer: CacheInvalidationProducer?) {
        this.cacheInvalidationProducer = producer
    }

    private fun getAsset(assetId: String): Asset {
        val asset = assetFinder.find(assetId)
        return asset
    }

    @Transactional
    fun getMarketData(
        assetId: String,
        date: LocalDate,
        closePrice: BigDecimal = BigDecimal.ZERO
    ): Optional<MarketData> {
        val asset = getAsset(assetId)
        val existing =
            marketDataRepo.findByAssetIdAndPriceDate(
                asset.id,
                date
            )

        // For private market assets with a provided price, allow to create or update
        if (asset.market.code == PrivateMarketDataProvider.ID && closePrice != BigDecimal.ZERO) {
            return handlePrivateMarketPrice(asset, closePrice, date, existing)
        }

        return existing
    }

    private fun handlePrivateMarketPrice(
        asset: Asset,
        closePrice: BigDecimal,
        date: LocalDate,
        existing: Optional<MarketData>
    ): Optional<MarketData> {
        val marketData =
            if (existing.isPresent) {
                // Update existing price
                val md = existing.get()
                md.close = closePrice
                md
            } else {
                // Create new price
                MarketData(
                    asset = asset,
                    close = closePrice,
                    priceDate = date,
                    source = "USER"
                )
            }
        return Optional.of(marketDataRepo.save(marketData))
    }

    /**
     * Persistence and distribution of MarketData objects.
     * If previousClose is not provided by the API, it is calculated from the previous day's close.
     *
     * IMPORTANT: Prices with close <= 0 are rejected as invalid data from the provider.
     * A zero or negative price indicates a provider issue and should never be stored.
     */
    @Transactional
    fun handle(priceResponse: PriceResponse): Iterable<MarketData> {
        val createSet =
            priceResponse.data
                .filter { !cashUtils.isCash(it.asset) && isValidPrice(it) }
                .filter { marketData ->
                    // Skip if already exists (idempotent operation)
                    marketDataRepo.countByAssetIdAndPriceDate(
                        marketData.asset.id,
                        marketData.priceDate
                    ) == 0L
                }.map { marketData ->
                    enrichWithPreviousClose(marketData)
                }

        priceResponse.data
            .filter { !cashUtils.isCash(it.asset) && isCorporateEvent(it) }
            .forEach { marketData ->
                eventProducer?.write(marketData)
            }

        return if (createSet.isEmpty()) {
            createSet
        } else {
            val saved = marketDataRepo.saveAll(createSet)
            val dates = createSet.map { it.priceDate }.distinct()
            dates.forEach { cacheInvalidationProducer?.sendPriceEvent(it) }
            saved
        }
    }

    /**
     * Normalises price fields for the ex-date of a split and derives previousClose when missing.
     *
     * Behaviour:
     *  - Always consults yesterday's stored close so we can detect an ex-date by comparing the
     *    row's `split` to the previous row's `split` (a different value means this is a genuine
     *    ex-date; a sticky identical value means the provider kept the flag around).
     *  - On a genuine ex-date:
     *      • If the provider's `close` looks unadjusted (> 2× the expected post-split level)
     *        the OHLC fields are divided by the split to sit on the post-split basis.
     *      • `previousClose` is rebased onto the post-split basis — whether it was supplied
     *        by the provider (typically raw, e.g. Alpha) or derived from yesterday's stored
     *        close. `change` / `changePercent` are recomputed from the adjusted values.
     *  - Off ex-date, we only fill `previousClose` (and its derivatives) from the prior row
     *    when the provider didn't supply a value — preserving the provider's own values when
     *    it did.
     *
     * Example: Monday $1000 → Tuesday 25:1 split → close $40 stored, previousClose $40 stored,
     * change 0, changePercent 0.
     */
    private fun enrichWithPreviousClose(marketData: MarketData): MarketData {
        val previousDayData =
            marketDataRepo.findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(
                marketData.asset,
                marketData.priceDate
            )
        if (!previousDayData.isPresent) {
            return marketData
        }

        // Some providers (e.g. MarketStack) deliver the ex-date row without a
        // split flag, leaving previousClose on the pre-split basis and the
        // change/changePercent at -1/N. Cross-check Alpha's split feed (cached)
        // and stamp the row before the rebase logic below kicks in.
        // Gate the lookup behind a "likely missing split" anomaly check so we
        // don't hit Alpha for every normal split=1 row (the common case).
        if (marketData.split.compareTo(BigDecimal.ONE) == 0 &&
            looksLikeMissingSplit(marketData, previousDayData.get())
        ) {
            knownSplitFor(marketData.asset, marketData.priceDate)?.let { factor ->
                marketData.split = factor
            }
        }

        val split = marketData.split
        val hasSplit =
            split.compareTo(BigDecimal.ZERO) > 0 && split.compareTo(BigDecimal.ONE) != 0
        val previousSplit = previousDayData.get().split
        // Only treat this row as a split ex-date when the previous day's row did not
        // already carry the same split factor. Some providers keep the split value
        // "sticky" on subsequent rows — re-adjusting those rows would divide prices twice.
        val isSplitExDate = hasSplit && previousSplit.compareTo(split) != 0
        val providerGavePreviousClose =
            marketData.previousClose.compareTo(BigDecimal.ZERO) != 0

        // Off an ex-date we respect the provider's values; only derive previousClose when absent.
        if (!isSplitExDate && providerGavePreviousClose) {
            return marketData
        }

        val rawPreviousClose =
            if (providerGavePreviousClose) {
                marketData.previousClose
            } else {
                previousDayData.get().close
            }

        if (isSplitExDate) {
            // Close looks unadjusted when it's more than 2× the expected post-split level
            // derived from yesterday's raw close. Rebase OHLC onto the post-split basis.
            val closeLooksUnadjusted =
                previousDayData.get().close.compareTo(BigDecimal.ZERO) > 0 &&
                    marketData.close.compareTo(
                        previousDayData
                            .get()
                            .close
                            .divide(split, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal("2"))
                    ) > 0
            if (closeLooksUnadjusted) {
                marketData.close =
                    marketData.close.divide(split, 6, java.math.RoundingMode.HALF_UP)
                if (marketData.open.compareTo(BigDecimal.ZERO) > 0) {
                    marketData.open =
                        marketData.open.divide(split, 6, java.math.RoundingMode.HALF_UP)
                }
                if (marketData.high.compareTo(BigDecimal.ZERO) > 0) {
                    marketData.high =
                        marketData.high.divide(split, 6, java.math.RoundingMode.HALF_UP)
                }
                if (marketData.low.compareTo(BigDecimal.ZERO) > 0) {
                    marketData.low =
                        marketData.low.divide(split, 6, java.math.RoundingMode.HALF_UP)
                }
            }
        }

        val splitAdjustedPreviousClose =
            if (isSplitExDate) {
                rawPreviousClose.divide(split, 6, java.math.RoundingMode.HALF_UP)
            } else {
                rawPreviousClose
            }
        marketData.previousClose = splitAdjustedPreviousClose
        marketData.change = marketData.close.subtract(splitAdjustedPreviousClose)
        if (splitAdjustedPreviousClose.compareTo(BigDecimal.ZERO) != 0) {
            marketData.changePercent =
                marketData.change.divide(
                    splitAdjustedPreviousClose,
                    6,
                    java.math.RoundingMode.HALF_UP
                )
        }
        log.trace(
            "Enriched price for {} on {} (split={}, prevSplit={}, exDate={}): " +
                "prevRaw={} prevAdj={} close={} -> change={}, changePercent={}",
            marketData.asset.code,
            marketData.priceDate,
            split,
            previousSplit,
            isSplitExDate,
            rawPreviousClose,
            splitAdjustedPreviousClose,
            marketData.close,
            marketData.change,
            marketData.changePercent
        )
        return marketData
    }

    private fun isCorporateEvent(marketData: MarketData): Boolean =
        marketData.asset.isKnown && (isDividend(marketData) || isSplit(marketData))

    /**
     * Validates that a price is valid for storage.
     * A valid price must have a close price > 0.
     * Zero or negative prices indicate provider issues and are rejected.
     */
    private fun isValidPrice(marketData: MarketData): Boolean {
        val isValid = marketData.close > BigDecimal.ZERO
        if (!isValid) {
            log.warn(
                "Rejecting invalid price for {} on {}: close={} (must be > 0)",
                marketData.asset.code,
                marketData.priceDate,
                marketData.close
            )
        }
        return isValid
    }

    /**
     * Delete all prices.  Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete ALL market data history.
     */
    @Transactional
    fun purge() {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: purge() method called in non-test environment! " +
                    "This would delete ALL market data history. " +
                    "Current profile: ${System.getProperty("spring.profiles.active")} " +
                    "Classpath: ${System.getProperty("java.class.path")}"
            )
        }

        log.warn("PURGE OPERATION: Deleting ALL market data - this should only happen in tests!")
        marketDataRepo.deleteAll()
    }

    /**
     * Delete specific market data record.  Supports testing ONLY!
     *
     * CRITICAL: This method should NEVER be called in production!
     * It will permanently delete market data history.
     */
    @Transactional
    fun purge(marketData: MarketData) {
        // SAFEGUARD: Only allow purge in test environments
        if (!TestEnvironmentUtils.isTestEnvironment()) {
            throw IllegalStateException(
                "CRITICAL ERROR: purge(MarketData) method called in non-test environment! " +
                    "This would delete market data history. " +
                    "Asset: ${marketData.asset.name}, Date: ${marketData.priceDate} " +
                    "Current profile: ${System.getProperty("spring.profiles.active")}"
            )
        }

        log.warn(
            "PURGE OPERATION: Deleting market data for ${marketData.asset.name} on ${marketData.priceDate} - this should only happen in tests!"
        )
        marketDataRepo.deleteById(marketData.id)
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
        return result
    }

    /**
     * Apply [SplitAdjuster] to a chronological [MarketData] series and write the
     * adjusted OHLC back into the returned rows. Mirrors what [getPriceHistory]
     * does for the `/prices/{id}/history` endpoint so the bulk read path that
     * feeds `svc-position` performance charts sees the same rebased basis.
     */
    private fun adjustSeriesForSplits(series: List<MarketData>): List<MarketData> {
        if (series.size < 2) return series
        val asset = series.first().asset
        val from = series.first().priceDate
        val to = series.last().priceDate
        val splitEvents = collectSplitEvents(asset, from, to)
        val adjusted = SplitAdjuster.adjust(series.map(PricePoint::from), splitEvents)
        if (adjusted === series) return series
        return series.zip(adjusted).map { (md, point) ->
            if (point.close.compareTo(md.close) == 0 &&
                point.open.compareTo(md.open) == 0 &&
                point.high.compareTo(md.high) == 0 &&
                point.low.compareTo(md.low) == 0
            ) {
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
     * Get market data for assets on a specific date (exact match only).
     */
    @Transactional
    fun getMarketData(
        assets: Collection<Asset>,
        date: LocalDate
    ): List<MarketData> =
        marketDataRepo.findByAssetInAndPriceDate(
            assets,
            date
        )

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
        val splitEvents = collectSplitEvents(asset, from, to)
        return PriceHistoryResponse(
            asset = asset,
            prices = SplitAdjuster.adjust(rawPrices, splitEvents)
        )
    }

    // True when today's close has dropped sharply enough versus yesterday to
    // suggest the provider may have omitted a split flag on the ex-date row.
    // Uses a 30% gap threshold — small enough to catch a 2-for-1 split (50%
    // drop) and well above any plausible single-day organic move, so we keep
    // Alpha lookups limited to genuinely suspicious rows.
    private fun looksLikeMissingSplit(
        marketData: MarketData,
        previousDay: MarketData
    ): Boolean {
        if (previousDay.close.compareTo(BigDecimal.ZERO) <= 0) return false
        if (marketData.close.compareTo(BigDecimal.ZERO) <= 0) return false
        val anomalyThreshold = previousDay.close.multiply(MISSING_SPLIT_DROP_THRESHOLD)
        return marketData.close.compareTo(anomalyThreshold) < 0
    }

    // Returns Alpha's known split factor for the asset on `date` when one
    // exists, or null otherwise. Lets enrichWithPreviousClose recover from
    // providers that omit the split flag on the ex-date row (e.g. MarketStack
    // on VUG 2026-04-21).
    private fun knownSplitFor(
        asset: Asset,
        date: LocalDate
    ): BigDecimal? {
        val service = alphaEventService ?: return null
        return try {
            service
                .getEvents(asset)
                .data
                .asSequence()
                .filter(::isSplit)
                .firstOrNull { it.priceDate == date }
                ?.split
                ?.takeIf { it.compareTo(BigDecimal.ONE) != 0 && it.signum() > 0 }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug(
                "Split lookup failed for {} on {}: {}",
                asset.code,
                date,
                e.message
            )
            null
        }
    }

    /**
     * Pulls known split events for the asset within the requested range.
     *
     * The historical backfill via Alpha's TIME_SERIES_DAILY does not carry
     * split coefficients, so the database can hold raw pre-split closes
     * with split=1 across the board. AlphaEventService (cached) returns
     * splits sourced from TIME_SERIES_DAILY_ADJUSTED which contains the
     * coefficients on each ex-date. Without this, [SplitAdjuster] would
     * have nothing to detect when the price rows themselves are bare.
     */
    private fun collectSplitEvents(
        asset: Asset,
        from: LocalDate,
        to: LocalDate
    ): List<SplitAdjuster.SplitEvent> {
        val service = alphaEventService ?: return emptyList()
        return try {
            service
                .getEvents(asset)
                .data
                .asSequence()
                .filter(::isSplit)
                .filter { !it.priceDate.isBefore(from) && !it.priceDate.isAfter(to) }
                .map { SplitAdjuster.SplitEvent(it.priceDate, it.split) }
                .toList()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn(
                "Failed to load split events for {}: {}",
                asset.code,
                e.message
            )
            emptyList()
        }
    }

    /**
     * Get the most recent market data for an asset on or before the given date.
     * Used as a fallback when the market was closed on the requested date.
     */
    @Transactional
    fun getLatestMarketData(
        asset: Asset,
        date: LocalDate
    ): MarketData? =
        marketDataRepo
            .findTop1ByAssetAndPriceDateLessThanEqualOrderByPriceDateDesc(asset, date)
            .orElse(null)

    /**
     * SAFEGUARD: Count market data records for an asset on a specific date
     */
    @Transactional
    fun getMarketDataCount(
        assetId: String,
        date: LocalDate
    ): Long = marketDataRepo.countByAssetIdAndPriceDate(assetId, date)

    companion object {
        // Today's close must be below this fraction of yesterday's close before
        // we treat the row as a candidate for a missing split flag and consult
        // Alpha. 0.70 catches 2:1 splits (50% drop) with comfortable headroom
        // over any realistic organic single-day move.
        private val MISSING_SPLIT_DROP_THRESHOLD = BigDecimal("0.70")

        // How far before the earliest requested date to extend the bulk window
        // load. Covers long weekends + bank-holiday clusters so the in-memory
        // nearest-prior fallback has something to resolve to. 10 days is plenty
        // for any developed market.
        private const val FALLBACK_LOOKBACK_DAYS = 10L
    }
}