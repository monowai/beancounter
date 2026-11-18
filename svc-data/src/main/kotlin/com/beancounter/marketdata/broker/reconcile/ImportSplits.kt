package com.beancounter.marketdata.broker.reconcile

import com.beancounter.marketdata.price.MarketDataRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The splits between a back-dated holding's date and the statement's record date. A statement
 * shows today's quantity, which already reflects those splits; recorded on the earlier date it
 * must be in that date's share basis, or the split transactions multiply it a second time.
 */
@Component
class ImportSplits(
    private val marketDataRepo: MarketDataRepo,
    private val providerHistory: ProviderHistory
) {
    private val log = LoggerFactory.getLogger(ImportSplits::class.java)

    /**
     * What one share held on [from] has become by [to]: the product of the splits dated on or
     * after [from] and before [to]. A split on [from] counts, as a holding added that day is
     * split with it. Stored price history is read first; where it does not reach back to
     * [from], the provider's split history is asked, and kept nowhere. 1 when none is known.
     */
    fun factor(
        assetId: String,
        from: LocalDate,
        to: LocalDate
    ): BigDecimal {
        val stored = marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1))
        val splits =
            stored
                .filter { it.split.compareTo(BigDecimal.ONE) != 0 }
                .associate { it.priceDate to it.split }
                .toMutableMap()
        val storedFrom = stored.minOfOrNull { it.priceDate }
        if (storedFrom == null || storedFrom.isAfter(from.plusDays(STORED_GAP_DAYS))) {
            older(assetId, from, storedFrom ?: to).forEach { (date, split) -> splits.putIfAbsent(date, split) }
        }
        return splits.values.fold(BigDecimal.ONE) { product, split -> product.multiply(split) }
    }

    private fun older(
        assetId: String,
        from: LocalDate,
        before: LocalDate
    ): List<Pair<LocalDate, BigDecimal>> =
        runCatching { providerHistory.splitsBetween(assetId, from, before) }
            .onFailure { log.warn("Split history unavailable for asset {} from {}", assetId, from, it) }
            .getOrDefault(emptyList())

    private companion object {
        /** Stored history starting within this many days of the trade date covers it (weekends, holidays). */
        const val STORED_GAP_DAYS = 7L
    }
}