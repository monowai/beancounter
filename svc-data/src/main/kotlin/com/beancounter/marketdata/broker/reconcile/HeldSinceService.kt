package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.price.MarketDataService
import com.beancounter.marketdata.price.history.PriceHistoryReader
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Suggests when each imported holding was opened, from its cost and the asset's price
 * history: the window and backfill the price chart uses. Records no transaction.
 */
@Service
class HeldSinceService(
    private val priceHistoryReader: PriceHistoryReader,
    private val marketDataService: MarketDataService,
    private val providerHistory: ProviderHistory,
    private val confirmedAssets: ConfirmedAssets,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(HeldSinceService::class.java)

    fun suggest(request: HeldSinceRequest): List<HeldSinceSuggestion> {
        if (request.rows.size > ReconcileService.MAX_ROWS) {
            throw BusinessException(
                "A statement holds at most ${ReconcileService.MAX_ROWS} rows, not ${request.rows.size}"
            )
        }
        val ids = request.rows.map(::assetIdOf)
        // A statement can list one asset in several rows: read, or backfill, each asset once.
        val closesByAsset = ids.filterNotNull().distinct().associateWith { closes(it) }
        val olderByAsset = mutableMapOf<String, List<Pair<LocalDate, BigDecimal>>>()
        return request.rows.zip(ids).map { (query, assetId) ->
            if (assetId == null) return@map HeldSinceSuggestion(null, null, null)
            val closes = closesByAsset.getValue(assetId)
            val estimate =
                HeldSince.estimate(closes, query.costPerShare).let { inWindow ->
                    // The cost is below every price in the window: the holding may be older.
                    val earliest = inWindow.before ?: return@let inWindow
                    val older = olderByAsset.getOrPut(assetId) { older(assetId, earliest) }
                    if (older.isEmpty()) inWindow else HeldSince.estimate(older + closes, query.costPerShare)
                }
            HeldSinceSuggestion(assetId, estimate.heldSince, estimate.before)
        }
    }

    /**
     * The asset a query names. A listing new to Beancounter is created, as recording it would,
     * so its prices can be fetched; null when the query names none, or it cannot be created.
     */
    private fun assetIdOf(query: HeldSinceQuery): String? {
        if (!query.assetId.isNullOrBlank()) return query.assetId
        val market = query.market?.takeIf { it.isNotBlank() } ?: return null
        val code = query.code?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { confirmedAssets.idFor(market, code) }
            .onFailure { log.warn("No asset for {}:{} to suggest a date from", market, code, it) }
            .getOrNull()
    }

    /** Prices from before the window, from the provider; none when it cannot say. */
    private fun older(
        assetId: String,
        before: LocalDate
    ): List<Pair<LocalDate, BigDecimal>> =
        runCatching { providerHistory.closesBefore(assetId, before) }
            .onFailure { log.warn("Older price history unavailable for asset {}", assetId, it) }
            .getOrDefault(emptyList())

    /**
     * The asset's closes over the backfill window. Prices missing from the window, such as
     * the years before the first one stored, are fetched from the provider first; a long
     * holding needs them.
     */
    private fun closes(assetId: String): List<Pair<LocalDate, BigDecimal>> {
        val today = dateUtils.date
        val from = today.minusYears(MAX_BACKFILL_YEARS)
        runCatching { marketDataService.backFill(assetId, from) }
            .onFailure { log.warn("Price history backfill failed for asset {}", assetId, it) }
        return priceHistoryReader.getPriceHistory(assetId, from, today).prices.map { it.priceDate to it.close }
    }
}