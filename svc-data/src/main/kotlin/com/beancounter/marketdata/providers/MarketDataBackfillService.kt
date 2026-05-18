package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.defaultBackfillFrom
import com.beancounter.marketdata.trn.TrnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for handling market data backfill operations.
 *
 * Three responsibilities sit here so every caller (controller, coordinator,
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
 * 3. **No PRICE_HISTORY invalidation from this path** — the backfill
 *    only ever INSERTS rows (PriceService.handle filters duplicates by
 *    `(assetId, priceDate)` before insert), so it never modifies a
 *    price inside the existing svc-position snapshot range. Anchor
 *    extension lands rows OUTSIDE `[priorDbMin, priorDbMax]` (prefix
 *    or suffix); duplicate dates are dropped. The cache invalidation
 *    event is intentionally NOT sent from here — sending it with the
 *    widened `fromDate` would wipe years of cached snapshots and force
 *    svc-position to recompute the full range in one prefetch (OOM
 *    incident on 2026-05-18, POSITION-3T..3V). Mid-range gap fills are
 *    rare; svc-position cache self-heals on lazy refresh. Other code
 *    paths that genuinely modify in-range prices (dividend adjustments,
 *    manual overrides) should fire their own invalidation events.
 *    [CacheInvalidationProducer] is injected so callers can later opt
 *    into invalidation if a use case emerges, but [backFill] does not
 *    call it.
 */
@Service
class MarketDataBackfillService(
    private val providerUtils: ProviderUtils,
    private val priceService: PriceService,
    private val assetFinder: AssetFinder,
    private val marketDataRepo: MarketDataRepo,
    private val trnRepository: TrnRepository,
    @Suppress("unused") private val cacheInvalidationProducer: CacheInvalidationProducer? = null,
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
        val priorDbMin = marketDataRepo.findEarliestPriceDateByAssetId(asset.id)
        val priorDbMax = marketDataRepo.findLatestPriceDateByAssetId(asset.id)
        if (isCovered(priorDbMin, priorDbMax, anchored, today)) {
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
        // PRICE_HISTORY invalidation intentionally omitted — see class kdoc.
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
        dbMin: LocalDate?,
        dbMax: LocalDate?,
        fromDate: LocalDate,
        today: LocalDate
    ): Boolean {
        if (dbMin == null || dbMax == null) return false
        val coversStart = !dbMin.isAfter(fromDate)
        val coversEnd = !dbMax.isBefore(today.minusDays(1))
        return coversStart && coversEnd
    }

    private fun getAsset(assetId: String): Asset = assetFinder.find(assetId)
}