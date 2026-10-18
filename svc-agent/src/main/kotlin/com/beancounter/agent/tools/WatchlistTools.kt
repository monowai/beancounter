package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.agent.client.WatchlistEntry
import com.beancounter.client.services.ClassificationClient
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

/**
 * The user's watchlist for a momentum and fundamentals brief: each watched asset with its
 * sector, the user's entry and target, and [Momentum] read from the asset's own history.
 *
 * Watched assets are not holdings, so there are no quantities or amounts to scrub; prices
 * and percentages are what the user tracks.
 */
@Service
class WatchlistTools(
    private val watchlistClient: WatchlistClient,
    private val classificationClient: ClassificationClient,
    private val dateUtils: DateUtils = DateUtils()
) {
    companion object {
        /** Keeps the tool result, and the history reads behind it, bounded. */
        const val MAX_ITEMS = 25

        // Thirteen months, so the twelve-month return has a close on or before its lookback date.
        private const val HISTORY_MONTHS = 13L
        private val log = LoggerFactory.getLogger(WatchlistTools::class.java)
    }

    @Tool(description = WATCHLIST_DESC)
    fun getWatchlist(): WatchlistBrief {
        val entries = watchlistClient.getWatchlist()
        val shown = entries.take(MAX_ITEMS)
        if (shown.isEmpty()) return WatchlistBrief(emptyList(), 0, false)
        val classifications = classificationClient.getClassifications(shown.map { it.asset.id }).data
        val from = dateUtils.date.minusMonths(HISTORY_MONTHS).toString()
        // Sequential on purpose: an asset never priced before backfills on its first history
        // read, and a burst of those at once is what exhausts svc-data's heap.
        val items = shown.map { describe(it, classifications[it.asset.id], momentum(it, from)) }
        return WatchlistBrief(items, entries.size, entries.size > shown.size)
    }

    private fun momentum(
        entry: WatchlistEntry,
        from: String
    ): Momentum =
        try {
            Momentum.of(watchlistClient.getCloses(entry.asset.id, from).toList())
        } catch (e: RestClientException) {
            log.warn("No price history for watched asset {}: {}", entry.asset.id, e.message)
            Momentum()
        }

    private fun describe(
        entry: WatchlistEntry,
        classification: AssetClassificationSummary?,
        momentum: Momentum
    ) = WatchedAsset(
        code = entry.asset.code,
        market = entry.asset.market.code,
        name = entry.asset.name,
        category = entry.asset.effectiveReportCategory,
        sector = classification?.sector,
        industry = classification?.industry,
        currency = entry.currency.code,
        price = entry.price?.toDouble(),
        priceDate = entry.priceDate,
        addedPrice = entry.addedPrice.toDouble(),
        addedOn = entry.addedOn,
        targetPrice = entry.targetPrice?.toDouble(),
        changeOnDay = entry.changeOnDayPercent?.toDouble(),
        changeSinceAdded = entry.changeSinceAddedPercent?.toDouble(),
        toTarget = entry.toTarget?.toDouble(),
        momentum = momentum
    )
}

/** The watched assets, at most [WatchlistTools.MAX_ITEMS]; [count] is the full list's size. */
data class WatchlistBrief(
    val items: List<WatchedAsset>,
    val count: Int,
    val truncated: Boolean
)

/**
 * One watched asset. Prices are per unit in [currency]; [changeOnDay], [changeSinceAdded]
 * and [toTarget] are fractions (0.25 = 25%) and null when the asset has no price.
 */
data class WatchedAsset(
    val code: String,
    val market: String,
    val name: String?,
    val category: String,
    val sector: String?,
    val industry: String?,
    val currency: String,
    val price: Double?,
    val priceDate: String?,
    val addedPrice: Double,
    val addedOn: String,
    val targetPrice: Double?,
    val changeOnDay: Double?,
    val changeSinceAdded: Double?,
    val toTarget: Double?,
    val momentum: Momentum
)

const val WATCHLIST_DESC =
    "The user's watchlist: assets they track but do not necessarily hold. For each asset: " +
        "code, market, name, category, sector and industry (null when unclassified), currency, " +
        "latest price and its date, the price when added (addedPrice, addedOn), the user's " +
        "targetPrice, and as fractions changeOnDay, changeSinceAdded and toTarget (distance " +
        "from price up to target; negative means the price is above target). momentum holds " +
        "return1m/3m/6m/12m, vsSma50/vsSma200 (latest close against its 50- and 200-day " +
        "averages) and fromHigh52w/fromLow52w, all fractions, null where history is too short. " +
        "count is the full list size; truncated is true when only the first items are returned."