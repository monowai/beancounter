package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.price.MarketDataService
import com.beancounter.marketdata.price.history.PriceHistoryReader
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Suggests when each imported holding was opened, from its cost and the asset's price
 * history: the window and backfill the price chart uses. Records no transaction.
 */
@Service
class HeldSinceService(
    private val priceHistoryReader: PriceHistoryReader,
    private val marketDataService: MarketDataService,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(HeldSinceService::class.java)

    fun suggest(request: HeldSinceRequest): List<HeldSinceSuggestion> {
        if (request.rows.size > ReconcileService.MAX_ROWS) {
            throw BusinessException(
                "A statement holds at most ${ReconcileService.MAX_ROWS} rows, not ${request.rows.size}"
            )
        }
        // A statement can list one asset in several rows: read, or backfill, each asset once.
        val closesByAsset =
            request.rows
                .map { it.assetId }
                .distinct()
                .associateWith { closes(it) }
        return request.rows.map { query ->
            val estimate = HeldSince.estimate(closesByAsset.getValue(query.assetId), query.costPerShare)
            HeldSinceSuggestion(query.assetId, estimate.heldSince, estimate.before)
        }
    }

    /**
     * The asset's closes over the backfill window. Prices missing from the window, such as
     * the years before the first one stored, are fetched from the provider first; a long
     * holding needs them.
     */
    private fun closes(assetId: String): List<Pair<java.time.LocalDate, java.math.BigDecimal>> {
        val today = dateUtils.date
        val from = today.minusYears(MAX_BACKFILL_YEARS)
        runCatching { marketDataService.backFill(assetId, from) }
            .onFailure { log.warn("Price history backfill failed for asset {}", assetId, it) }
        return priceHistoryReader.getPriceHistory(assetId, from, today).prices.map { it.priceDate to it.close }
    }
}