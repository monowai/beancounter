package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.contracts.RepairSplitsResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Splits as prices see them: the provider's split events, the `split` column stamped from
 * them, and the cumulative factors that put a past price on today's share basis.
 */
@Service
class PriceSplits(
    private val marketDataRepo: MarketDataRepo,
    private val assetFinder: AssetFinder,
    private val eventServiceFacade: EventServiceFacade,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    private val log = LoggerFactory.getLogger(PriceSplits::class.java)

    /**
     * Cumulative split factors for many assets in two queries. For each asset in [datesByAsset]
     * and each of its dates, the product of splits with an ex-date after that date up to [to],
     * read from the stored `split` column (no provider call). Divide a price observed on a date
     * by its factor to put it on the [to] share basis.
     *
     * Each asset's series is its rows from the earliest date to [to], led by its nearest row
     * before that window, so a split stamp at the window's start is judged against a real
     * predecessor (see [SplitAdjuster.factorAfter]).
     */
    fun splitFactorsAfter(
        datesByAsset: Map<Asset, Collection<LocalDate>>,
        to: LocalDate
    ): Map<String, Map<LocalDate, BigDecimal>> {
        val earliest = datesByAsset.values.flatten().minOrNull() ?: return emptyMap()
        val assetIds = datesByAsset.keys.map { it.id }
        val window =
            marketDataRepo
                .findSplitHistory(assetIds, earliest, to)
                .groupBy({ it.assetId }, { it.priceDate to it.split })
        val predecessors =
            marketDataRepo
                .findLatestByAssetInAndPriceDateLessThanEqual(datesByAsset.keys, earliest.minusDays(1))
                .groupBy({ it.asset.id }, { it.priceDate to it.split })
        return datesByAsset.entries.associate { (asset, dates) ->
            val series = splitSeries(predecessors[asset.id].orEmpty() + window[asset.id].orEmpty())
            asset.id to dates.distinct().associateWith { SplitAdjuster.factorAfter(series, it) }
        }
    }

    // One chronological (date, split) point per date; several providers can hold a row for
    // the same date, and any non-1 marker wins.
    private fun splitSeries(rows: List<Pair<LocalDate, BigDecimal>>): List<Pair<LocalDate, BigDecimal>> =
        rows
            .groupBy({ it.first }, { it.second })
            .map { (date, splits) ->
                date to (splits.firstOrNull { it.compareTo(BigDecimal.ONE) != 0 } ?: BigDecimal.ONE)
            }.sortedBy { it.first }

    // Returns the provider's known split factor for the asset on `date` when one
    // exists, or null otherwise. Lets [DayChange] recover from
    // providers that omit the split flag on the ex-date row (e.g. MarketStack
    // on VUG 2026-04-21). Routes via [EventServiceFacade] so US assets covered
    // by EODHD pick up splits Alpha misses (e.g. SCHG 4:1 on 2024-10-11).
    fun knownSplitFor(
        asset: Asset,
        date: LocalDate
    ): BigDecimal? =
        try {
            providerEvents(asset)
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
    fun collectSplitEvents(
        asset: Asset,
        from: LocalDate,
        to: LocalDate
    ): List<SplitAdjuster.SplitEvent> =
        try {
            providerEvents(asset)
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

    // Source of corporate events for an asset: [EventServiceFacade] routes each market to its
    // configured provider (EODHD covers SCHG splits Alpha doesn't).
    private fun providerEvents(asset: Asset): PriceResponse = eventServiceFacade.getEvents(asset.id)

    /**
     * Stamp the `split` column on each ex-date `MarketData` row from the
     * provider's known split events. After repair, `SplitAdjuster` can rebase
     * historical OHLC via column-transition detection on every read path
     * without re-querying the provider on each call.
     *
     * Idempotent: rows already carrying the matching factor are left alone.
     */
    @Transactional
    fun repairSplits(assetId: String): RepairSplitsResponse {
        val asset = assetFinder.find(assetId)
        val events =
            providerEvents(asset)
                .data
                .filter(::isSplit)
                .filter { it.split.compareTo(BigDecimal.ONE) != 0 && it.split.signum() > 0 }
        var stamped = 0
        var alreadyStamped = 0
        var missingRows = 0
        var earliest: LocalDate? = null
        for (event in events) {
            // A split impacts the asset regardless of provider: stamp every
            // row on the ex-date so multi-source coverage stays in sync (the
            // unique key is (source, asset_id, price_date)).
            val rows = marketDataRepo.findAllByAssetIdAndPriceDate(asset.id, event.priceDate)
            if (rows.isEmpty()) {
                missingRows++
                continue
            }
            var stampedThisEvent = false
            for (row in rows) {
                if (row.split.compareTo(event.split) == 0) {
                    alreadyStamped++
                    continue
                }
                row.split = event.split
                marketDataRepo.save(row)
                stamped++
                stampedThisEvent = true
            }
            if (stampedThisEvent && (earliest == null || event.priceDate.isBefore(earliest))) {
                earliest = event.priceDate
            }
        }
        if (stamped > 0 && earliest != null) {
            cacheInvalidationProducer?.sendPriceHistoryEvent(asset.id, earliest)
        }
        return RepairSplitsResponse(
            stamped = stamped,
            alreadyStamped = alreadyStamped,
            missingRows = missingRows
        )
    }
}