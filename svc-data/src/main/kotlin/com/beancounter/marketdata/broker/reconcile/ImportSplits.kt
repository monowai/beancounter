package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.price.MarketDataRepo
import com.beancounter.marketdata.price.history.PriceSplits
import com.beancounter.marketdata.providers.EventServiceFacade
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
    private val assetFinder: AssetFinder,
    private val priceSplits: PriceSplits,
    private val eventServiceFacade: EventServiceFacade
) {
    private val log = LoggerFactory.getLogger(ImportSplits::class.java)

    /**
     * What one share held on [from] has become by [to]: the product of the splits with an
     * ex-date on or after [from] and before [to]. A split on [from] counts, as a holding added
     * that day is split with it.
     *
     * Stored price history is read through [PriceSplits.splitFactorsAfter], which counts a
     * split once where its stamp first appears, however many rows repeat it. Where stored
     * history does not reach back to [from], the provider's split events (one row per event)
     * cover the years before it. 1 for an asset Beancounter does not have; null when the
     * provider could not be asked, so the older splits are unknown rather than absent.
     */
    fun factor(
        assetId: String,
        from: LocalDate,
        to: LocalDate
    ): BigDecimal? {
        val asset = assetFinder.findOrNull(assetId) ?: return BigDecimal.ONE
        val after = from.minusDays(1)
        val stored =
            priceSplits
                .splitFactorsAfter(mapOf(asset to listOf(after)), to.minusDays(1))[asset.id]
                ?.get(after) ?: BigDecimal.ONE
        // A row before [from] is needed: the first row of a series is never read as a split, so
        // history that opens on [from] would miss a split stamped that day.
        val storedReachesBack =
            marketDataRepo.findLatestByAssetInAndPriceDateLessThanEqual(listOf(asset), after).isNotEmpty()
        return if (storedReachesBack) stored else withOlder(stored, assetId, from, to)
    }

    /**
     * [stored] times the provider's splits from [from] up to and including the first stored
     * day: that first row has no predecessor, so a split stamped on it is not counted in
     * [stored], and the provider's event counts it once. Null when the provider cannot be asked.
     */
    private fun withOlder(
        stored: BigDecimal,
        assetId: String,
        from: LocalDate,
        to: LocalDate
    ): BigDecimal? {
        val storedFrom =
            marketDataRepo.findSplitHistory(listOf(assetId), from, to.minusDays(1)).minOfOrNull { it.priceDate }
        return olderSplits(assetId, from, storedFrom ?: to.minusDays(1))
            ?.fold(stored) { product, split -> product.multiply(split) }
    }

    private fun olderSplits(
        assetId: String,
        from: LocalDate,
        through: LocalDate
    ): List<BigDecimal>? =
        runCatching { eventServiceFacade.getEvents(assetId).data }
            .onFailure { log.warn("Split history unavailable for asset {} from {}", assetId, from, it) }
            .getOrNull()
            ?.filter { MarketData.isSplit(it) && it.split.signum() > 0 }
            ?.filter { !it.priceDate.isBefore(from) && !it.priceDate.isAfter(through) }
            ?.distinctBy { it.priceDate }
            ?.map { it.split }
}