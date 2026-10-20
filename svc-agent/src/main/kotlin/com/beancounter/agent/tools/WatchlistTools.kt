package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.agent.client.WatchlistEntry
import com.beancounter.client.services.ClassificationClient
import com.beancounter.common.contracts.AssetClassificationSummary
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The user's watchlist for a momentum and fundamentals brief: each watched asset with its
 * sector, the user's entry and target, [Momentum] read from the asset's own history, and
 * [Fundamentals] from its latest SEC filing where svc-data holds one.
 *
 * Watched assets are not holdings, so there are no quantities or amounts to scrub; prices
 * and percentages are what the user tracks.
 */
@Service
class WatchlistTools(
    private val watchlistClient: WatchlistClient,
    private val classificationClient: ClassificationClient,
    private val dateUtils: DateUtils = DateUtils(),
    private val nanoTime: () -> Long = System::nanoTime
) {
    companion object {
        /** Keeps the tool result, and the history reads behind it, bounded. */
        const val MAX_ITEMS = 25

        // A first read of a never-priced asset blocks on a provider backfill; past this budget
        // the remaining assets are described without momentum so the tool call stays responsive.
        private const val HISTORY_BUDGET_NANOS = 60_000_000_000L

        // SEC registrants file in USD; valuation against a price in another currency is meaningless.
        private const val FILING_CURRENCY = "USD"
        private val log = LoggerFactory.getLogger(WatchlistTools::class.java)
    }

    @Tool(description = WATCHLIST_DESC)
    fun getWatchlist(): WatchlistBrief {
        val entries = watchlistClient.getWatchlist()
        val shown = entries.take(MAX_ITEMS)
        if (shown.isEmpty()) return WatchlistBrief(emptyList(), 0, false, 0)
        val classifications = classifications(shown)
        val from = Momentum.historyFrom(dateUtils.date).toString()
        val deadline = nanoTime() + HISTORY_BUDGET_NANOS
        var skipped = 0
        // Sequential on purpose: an asset never priced before backfills on its first history
        // read, and a burst of those at once is what exhausts svc-data's heap.
        val items =
            shown.map { entry ->
                if (nanoTime() < deadline) {
                    describe(entry, classifications[entry.asset.id], momentum(entry, from), fundamentals(entry))
                } else {
                    skipped++
                    describe(entry, classifications[entry.asset.id], Momentum(), null)
                }
            }
        return WatchlistBrief(items, entries.size, entries.size > shown.size, skipped)
    }

    // Sector and industry are supplementary: a failed read leaves them null, never the brief.
    private fun classifications(entries: List<WatchlistEntry>): Map<String, AssetClassificationSummary> =
        try {
            classificationClient.getClassifications(entries.map { it.asset.id }).data
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            log.warn("No classifications for the watchlist: {}", e.message)
            emptyMap()
        }

    // svc-data's HTTP errors arrive as mapped runtime exceptions; one asset's failure must not
    // cost the user the whole watchlist.
    private fun momentum(
        entry: WatchlistEntry,
        from: String
    ): Momentum =
        try {
            Momentum.of(watchlistClient.getCloses(entry.asset.id, from).toList())
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            log.warn("No price history for watched asset {}: {}", entry.asset.id, e.message)
            Momentum()
        }

    // One svc-data read per asset, inside the same time budget; a failure only drops the fundamentals.
    private fun fundamentals(entry: WatchlistEntry): Fundamentals? =
        try {
            watchlistClient.getFundamentals(entry.asset.id)?.let { Fundamentals.of(it, valuationPrice(entry)) }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: RuntimeException
        ) {
            log.warn("No fundamentals for watched asset {}: {}", entry.asset.id, e.message)
            null
        }

    private fun valuationPrice(entry: WatchlistEntry): BigDecimal? =
        entry.price?.takeIf { entry.currency.code == FILING_CURRENCY && it.signum() > 0 }

    private fun describe(
        entry: WatchlistEntry,
        classification: AssetClassificationSummary?,
        momentum: Momentum,
        fundamentals: Fundamentals?
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
        momentum = momentum,
        fundamentals = fundamentals
    )
}

/**
 * The watched assets, at most [WatchlistTools.MAX_ITEMS]; [count] is the full list's size and
 * [momentumSkipped] how many trailing items ran out of history-read time.
 */
data class WatchlistBrief(
    val items: List<WatchedAsset>,
    val count: Int,
    val truncated: Boolean,
    val momentumSkipped: Int
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
    val momentum: Momentum,
    val fundamentals: Fundamentals?
)

/**
 * The latest fiscal year from the company's SEC filing, in USD. [priceToEarnings],
 * [dividendYield] and [marketCap] are measured at the watchlist price, so they are null when
 * there is no USD price; [priceToEarnings] is also null for a loss. [dividendYield] and
 * [netMargin] are fractions.
 */
data class Fundamentals(
    val source: String,
    val fiscalYear: Int,
    val fiscalYearEnd: String,
    val epsDiluted: Double?,
    val revenue: Double?,
    val netIncome: Double?,
    val dividendsPerShare: Double?,
    val sharesOutstanding: Long?,
    val priceToEarnings: Double?,
    val dividendYield: Double?,
    val marketCap: Double?,
    val netMargin: Double?
) {
    companion object {
        private const val SCALE = 4

        fun of(
            snapshot: AssetFundamentals,
            price: BigDecimal?
        ): Fundamentals {
            val eps = snapshot.epsDiluted
            val revenue = snapshot.revenue
            return Fundamentals(
                source = snapshot.source,
                fiscalYear = snapshot.fiscalYear,
                fiscalYearEnd = snapshot.fiscalYearEnd.toString(),
                epsDiluted = eps?.toDouble(),
                revenue = revenue?.toDouble(),
                netIncome = snapshot.netIncome?.toDouble(),
                dividendsPerShare = snapshot.dividendsPerShare?.toDouble(),
                sharesOutstanding = snapshot.sharesOutstanding,
                priceToEarnings = price?.let { p -> eps?.takeIf { it.signum() > 0 }?.let { ratio(p, it) } },
                dividendYield = price?.let { p -> snapshot.dividendsPerShare?.let { ratio(it, p) } },
                marketCap =
                    price?.let { p ->
                        snapshot.sharesOutstanding?.let { p.multiply(BigDecimal(it)).toDouble() }
                    },
                netMargin =
                    revenue?.takeIf { it.signum() > 0 }?.let { r ->
                        snapshot.netIncome?.let { ratio(it, r) }
                    }
            )
        }

        private fun ratio(
            numerator: BigDecimal,
            denominator: BigDecimal
        ) = numerator.divide(denominator, SCALE, RoundingMode.HALF_UP).toDouble()
    }
}

const val WATCHLIST_DESC =
    "The user's watchlist: assets they track but do not necessarily hold. For each asset: " +
        "code, market, name, category, sector and industry (null when unclassified), currency, " +
        "latest price and its date, the price when added (addedPrice, addedOn), the user's " +
        "targetPrice, and as fractions changeOnDay, changeSinceAdded and toTarget (distance " +
        "from price up to target; negative means the price is above target). momentum holds " +
        "asOf (the close date the readings are measured at), return1m/3m/6m/12m, " +
        "vsSma50/vsSma200 (latest close against its 50- and 200-day averages) and " +
        "fromHigh52w/fromLow52w, all fractions, plus the 52-week high and low closes " +
        "(high52w, low52w); each null where history is too short. fundamentals, " +
        "null when none is on file (non-US listings, funds) or the read failed, so null never " +
        "means the company has no filing; otherwise it holds the latest fiscal year from " +
        "the company's SEC filing in USD: source, fiscalYear, fiscalYearEnd, epsDiluted, revenue, " +
        "netIncome, dividendsPerShare, sharesOutstanding and netMargin (a fraction), plus at " +
        "the latest watchlist price priceToEarnings (null for a loss), dividendYield (a " +
        "fraction) and marketCap. count is the " +
        "full list size; truncated is true when only the first items are returned; " +
        "momentumSkipped counts trailing items whose history and fundamentals were not read in time."