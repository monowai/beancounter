package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.defaultBackfillFrom
import com.beancounter.marketdata.trn.TrnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for handling market data backfill operations.
 *
 * Two responsibilities sit here so every caller (controller, coordinator,
 * scheduler) gets the same behaviour:
 *
 * 1. **Anchor** — widen `fromDate` to the earliest tradeDate the asset has
 *    ever been held on, across every portfolio. This makes "ALL" wealth-
 *    performance charts work even when a newer portfolio caused the first
 *    backfill at a later anchor.
 * 2. **Skip when covered** — if the DB already has price rows spanning
 *    `[fromDate, today]`, do not call the external provider at all. EODHD
 *    charges per call, not per row, so a redundant call is one wasted
 *    quota unit per asset. Existing rows from any provider (incl. legacy
 *    ALPHA) count as coverage; provider lineage is not preserved.
 */
@Service
class MarketDataBackfillService(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
    private val assetFinder: AssetFinder,
    private val marketDataRepo: MarketDataRepo,
    private val trnRepository: TrnRepository,
    private val maxBackfillYears: Long = MAX_BACKFILL_YEARS
) {
    private val log = LoggerFactory.getLogger(MarketDataBackfillService::class.java)

    fun backFill(
        assetId: String,
        fromDate: LocalDate = defaultBackfillFrom()
    ) {
        backFill(getAsset(assetId), fromDate)
    }

    fun backFill(
        asset: Asset,
        fromDate: LocalDate = defaultBackfillFrom()
    ) {
        val today = LocalDate.now()
        val anchored = anchorFromDate(asset.id, fromDate, today)
        if (isCovered(asset.id, anchored, today)) {
            log.debug(
                "Backfill skipped — DB already covers [{}, today] for asset {}",
                anchored,
                asset.id
            )
            return
        }
        val byFactory = providerUtils.splitProviders(providerUtils.getInputs(listOf(asset)))
        for (marketDataProvider in byFactory.keys) {
            priceService.handle(marketDataProvider.backFill(asset, anchored))
        }
    }

    /**
     * Widen the caller's `fromDate` to the earliest SETTLED tradeDate for this
     * asset across every portfolio, then floor at `today - maxBackfillYears`.
     * If the asset has no recorded holdings (yet), keep the caller's date.
     */
    internal fun anchorFromDate(
        assetId: String,
        fromDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): LocalDate {
        val earliestHeld = trnRepository.findEarliestTradeDateByAssetId(assetId)
        val widened =
            if (earliestHeld != null && earliestHeld.isBefore(fromDate)) earliestHeld else fromDate
        val floor = today.minusYears(maxBackfillYears)
        return if (widened.isBefore(floor)) floor else widened
    }

    /**
     * True when the DB has price rows spanning `[fromDate, today - 1d]`. We allow
     * a one-day tail because today's price is filled by the live-price path, not
     * the historic backfill, so insisting on `dbMax == today` would re-fetch the
     * whole range every evening for no benefit.
     */
    private fun isCovered(
        assetId: String,
        fromDate: LocalDate,
        today: LocalDate
    ): Boolean {
        val dbMin = marketDataRepo.findEarliestPriceDateByAssetId(assetId) ?: return false
        val dbMax = marketDataRepo.findLatestPriceDateByAssetId(assetId) ?: return false
        val coversStart = !dbMin.isAfter(fromDate)
        val coversEnd = !dbMax.isBefore(today.minusDays(1))
        return coversStart && coversEnd
    }

    private fun getAsset(assetId: String): Asset = assetFinder.find(assetId)
}