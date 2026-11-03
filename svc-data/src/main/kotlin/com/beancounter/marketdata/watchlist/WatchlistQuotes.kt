package com.beancounter.marketdata.watchlist

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.providers.DayChange
import com.beancounter.marketdata.providers.LiveQuoteService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.PriceSplits
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Prices for watchlist items and the views built on them. Adding without a price goes
 * through the provider-aware price path. Read paths attach the live price over the latest
 * stored close (the scheduled refresh keeps it fresh) and compute every change server side.
 * Percentages are fractions at scale 6, matching MarketData.changePercent.
 */
@Service
class WatchlistQuotes(
    private val priceService: PriceService,
    private val priceSplits: PriceSplits,
    private val dayChange: DayChange,
    private val marketDataService: MarketDataService,
    private val liveQuoteService: LiveQuoteService,
    private val dateUtils: DateUtils
) {
    fun view(items: List<WatchlistItem>): WatchlistView {
        val prices = livePrices(items.map { it.asset })
        return WatchlistView(
            items =
                splitFactors(items).let { factors ->
                    items.map { itemView(it, prices[it.asset.id], factors) }
                }
        )
    }

    fun itemView(
        item: WatchlistItem,
        latest: MarketData?
    ): WatchlistItemView = itemView(item, latest, splitFactors(listOf(item)))

    /** The live price for one asset, or null when it has none. */
    fun live(asset: Asset): MarketData? = livePrices(listOf(asset))[asset.id]

    /** The cumulative split factor since the item was added. */
    fun addedFactor(item: WatchlistItem): BigDecimal =
        splitFactors(listOf(item)).getValue(item.id).getValue(item.addedOn)

    /**
     * Today's price for an asset added without a caller price. The fetched row is persisted
     * (and enriched) asynchronously; enrich a copy the same way so the response shows the day
     * change a later GET will read. Enrichment can rebase an unadjusted ex-date close; its
     * close becomes the stored addedPrice so it shares a basis with the persisted latest row.
     */
    fun priceToAdd(asset: Asset): MarketData {
        val fetched =
            fetchPrice(asset)
                ?: throw BusinessException("No price is available for asset ${asset.id}; supply addedPrice")
        return dayChange.enrichedCopy(fetched)
    }

    /**
     * Provider-aware price for today: resolves each provider's market date, reads the DB and
     * fetches-and-stores on a miss. Used only when adding without a caller price, so a
     * never-priced asset picked from search can still be stamped.
     */
    private fun fetchPrice(asset: Asset): MarketData? =
        marketDataService
            .getPriceResponse(PriceRequest.of(asset, TODAY))
            .data
            .firstOrNull { it.close.signum() > 0 }

    /** Live (delayed) prices over the stored closes: a watchlist is read for the price now. */
    private fun livePrices(assets: Collection<Asset>): Map<String, MarketData> =
        liveQuoteService.livePrices(assets, latestPrices(assets))

    /**
     * Latest close on or before today, ignoring rows without a positive close.
     */
    private fun latestPrices(assets: Collection<Asset>): Map<String, MarketData> =
        priceService
            .getLatestMarketData(assets, dateUtils.date)
            .filterValues { it.close.signum() > 0 }

    private fun itemView(
        item: WatchlistItem,
        latest: MarketData?,
        factorsByItem: Map<String, Map<LocalDate, BigDecimal>>
    ): WatchlistItemView {
        val targetOn = item.targetOn
        val factors = factorsByItem.getValue(item.id)
        val addedPrice = rebase(item.addedPrice, factors.getValue(item.addedOn))
        val target = item.targetPrice?.let { rebase(it, factors.getValue(requireNotNull(targetOn))) }
        val price = latest?.close
        val sinceAdded = price?.subtract(addedPrice)
        return WatchlistItemView(
            id = item.id,
            asset = item.asset,
            addedPrice = addedPrice,
            addedOn = item.addedOn,
            targetPrice = target,
            currency = item.asset.watchlistCurrency(),
            price = price,
            priceDate = latest?.priceDate,
            changeOnDay = latest?.change,
            changeOnDayPercent = latest?.changePercent,
            changeSinceAdded = sinceAdded,
            changeSinceAddedPercent = sinceAdded?.let { fraction(it, addedPrice) },
            toTarget = if (price == null || target == null) null else fraction(target.subtract(price), price),
            targetHitOn = item.targetHitOn
        )
    }

    /**
     * Stored prices sit on the share basis of the day they were entered, while the latest close
     * is on today's basis (SPLITS.md: the DB keeps raw OHLC). Factors per item and entry date
     * come from the stored split column.
     */
    private fun splitFactors(items: List<WatchlistItem>): Map<String, Map<LocalDate, BigDecimal>> {
        // Batched across every asset on the watchlist (DATA-6G: no per-item history reads).
        val datesByAsset =
            items
                .groupBy { it.asset.id }
                .values
                .associate { assetItems ->
                    assetItems.first().asset to assetItems.flatMap { listOfNotNull(it.addedOn, it.targetOn) }
                }
        val byAsset = priceSplits.splitFactorsAfter(datesByAsset, dateUtils.date)
        return items.associate { it.id to byAsset.getValue(it.asset.id) }
    }
}