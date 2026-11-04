package com.beancounter.marketdata.price

import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.price.history.PriceSplits
import com.beancounter.marketdata.price.history.SplitAdjuster
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * A price's change on the day: previousClose, change and changePercent, derived from the
 * stored row before it and rebased across a split ex-date.
 */
@Service
class DayChange(
    private val marketDataRepo: MarketDataRepo,
    private val priceSplits: PriceSplits
) {
    private val log = LoggerFactory.getLogger(DayChange::class.java)

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
    fun enrichWithPreviousClose(
        marketData: MarketData,
        previousDayData: MarketData?
    ): MarketData {
        if (previousDayData == null) {
            return marketData
        }

        stampMissingSplit(marketData, previousDayData)

        val split = marketData.split
        val hasSplit =
            split.compareTo(BigDecimal.ZERO) > 0 && split.compareTo(BigDecimal.ONE) != 0
        val previousSplit = previousDayData.split
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
                previousDayData.close
            }

        if (isSplitExDate) {
            SplitAdjuster.rebaseUnadjustedOhlc(marketData, previousDayData.close, split)
        }

        val splitAdjustedPreviousClose =
            if (isSplitExDate) {
                rawPreviousClose.divide(split, SplitAdjuster.SCALE, java.math.RoundingMode.HALF_UP)
            } else {
                rawPreviousClose
            }
        marketData.previousClose = splitAdjustedPreviousClose
        marketData.change = marketData.close.subtract(splitAdjustedPreviousClose)
        if (splitAdjustedPreviousClose.compareTo(BigDecimal.ZERO) != 0) {
            marketData.changePercent =
                marketData.change.divide(
                    splitAdjustedPreviousClose,
                    CHANGE_PERCENT_SCALE,
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

    /**
     * A copy of a fetched price, enriched (previousClose, change, changePercent) exactly as
     * [PriceService.handle] enriches it on persist, against the latest stored row before its date. Not
     * saved; lets a caller show the day change before the async save lands.
     */
    fun enrichedCopy(marketData: MarketData): MarketData =
        enrichWithPreviousClose(
            marketData.copy(),
            marketDataRepo
                .findTop1ByAssetAndPriceDateLessThanOrderByPriceDateDesc(marketData.asset, marketData.priceDate)
                .orElse(null)
        )

    // Some providers (e.g. MarketStack) deliver the ex-date row without a
    // split flag, leaving previousClose on the pre-split basis and the
    // change/changePercent at -1/N. Cross-check the event provider's split feed
    // (cached) and stamp the row before the rebase logic kicks in.
    // Gate the lookup behind a "likely missing split" anomaly check so we
    // don't hit the provider for every normal split=1 row (the common case).
    private fun stampMissingSplit(
        marketData: MarketData,
        previousDay: MarketData
    ) {
        if (marketData.split.compareTo(BigDecimal.ONE) == 0 && looksLikeMissingSplit(marketData, previousDay)) {
            priceSplits.knownSplitFor(marketData.asset, marketData.priceDate)?.let { factor ->
                marketData.split = factor
            }
        }
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

    companion object {
        // Today's close must be below this fraction of yesterday's close before
        // we treat the row as a candidate for a missing split flag and consult
        // the event provider. 0.70 catches 2:1 splits (50% drop) with comfortable
        // headroom over any realistic organic single-day move.
        private val MISSING_SPLIT_DROP_THRESHOLD = BigDecimal("0.70")

        internal const val CHANGE_PERCENT_SCALE = 6
    }
}