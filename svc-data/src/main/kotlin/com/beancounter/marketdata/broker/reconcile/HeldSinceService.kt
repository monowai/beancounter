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
        return request.rows.map { query ->
            val estimate = HeldSince.estimate(closes(query.assetId), query.costPerShare)
            HeldSinceSuggestion(query.assetId, estimate.heldSince, estimate.before)
        }
    }

    /** The asset's closes over the backfill window, fetched once from the provider if none are stored. */
    private fun closes(assetId: String): List<Pair<java.time.LocalDate, java.math.BigDecimal>> {
        val today = dateUtils.date
        val from = today.minusYears(MAX_BACKFILL_YEARS)
        val stored = priceHistoryReader.getPriceHistory(assetId, from, today).prices
        val prices =
            stored.ifEmpty {
                runCatching {
                    marketDataService.backFill(assetId, from)
                    priceHistoryReader.getPriceHistory(assetId, from, today).prices
                }.onFailure { log.warn("Price history backfill failed for asset {}", assetId, it) }
                    .getOrDefault(emptyList())
            }
        return prices.map { it.priceDate to it.close }
    }
}