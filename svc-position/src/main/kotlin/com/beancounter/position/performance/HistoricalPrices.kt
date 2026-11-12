package com.beancounter.position.performance

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.CashUtils
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val log = LoggerFactory.getLogger(HistoricalPrices::class.java)

private fun MarketData.key(): String = "${asset.market.code}:${asset.code}"

private fun Position.key(): String = "${asset.market.code}:${asset.code}"

/**
 * One performance pass's prices and FX rates, pre-fetched for every valuation date
 * and keyed by ISO date.
 */
class HistoricalPrices(
    private val priceCache: Map<String, Collection<MarketData>>,
    private val fxCache: Map<String, FxPairResults>,
    private val boundary: TwrBoundary
) {
    /**
     * The most recent [MarketData] per asset across every date in the pass.
     * PRIVATE-market assets (off-market property, etc.) are stamped by
     * PrivateMarketDataProvider with a single latest-price row and no daily
     * history, so the per-date lookup only ever finds a row on that one date;
     * every other valuation date needs this fallback instead of purchase cost.
     * A row whose close is not positive is no price, so it never hides an earlier one.
     * Computed once per pass, not per date.
     */
    private val latestPriceIndex: Map<String, MarketData> =
        buildMap {
            for (marketData in priceCache.values.flatten().filter { it.close.signum() > 0 }) {
                val existing = get(marketData.key())
                if (existing == null || marketData.priceDate.isAfter(existing.priceDate)) {
                    put(marketData.key(), marketData)
                }
            }
        }

    /** The fx-cache dates in order, binary-searched for the closest-rate lookup. Sorted once per pass. */
    private val fxDates: List<String> = fxCache.keys.sorted()

    /** Prices as they stood on [date]. */
    fun on(date: LocalDate): PricesOnDate {
        val dateStr = date.toString()
        return PricesOnDate(
            dateStr,
            (priceCache[dateStr] ?: emptyList()).associateBy { it.key() },
            { fxPair -> closestRate(dateStr, fxPair) },
            latestPriceIndex,
            boundary
        )
    }

    /**
     * The [fxPair] rate for the date, else the closest one the pass holds: the nearest
     * prior date, and failing that the nearest later one. The pass fetches rates for its
     * valuation dates, so a trn dated before the window (an in-specie transfer priced on
     * its own trade date) has no rate of its own; the figures are indicative, and the
     * closest rate is a better measure than none. A later rate is logged at WARN, so the
     * substitution leaves a trace. Null only when the pass holds no rate for the pair at all.
     */
    private fun closestRate(
        dateStr: String,
        fxPair: IsoCurrencyPair
    ): BigDecimal? =
        rateOn(dateStr, fxPair)
            ?: rateOnFirst(fxDates.asReversed().filter { it < dateStr }, fxPair)
            ?: laterRate(dateStr, fxPair)

    /** The [fxPair] rate from the first date after [dateStr] that holds one, logged at WARN. */
    private fun laterRate(
        dateStr: String,
        fxPair: IsoCurrencyPair
    ): BigDecimal? {
        val later = fxDates.filter { it > dateStr }
        return rateOnFirst(later, fxPair)?.also {
            log.warn("No {} rate on or before {}, using a later rate", fxPair, dateStr)
        }
    }

    /** The [fxPair] rate on the first of [dates] that holds one. */
    private fun rateOnFirst(
        dates: List<String>,
        fxPair: IsoCurrencyPair
    ): BigDecimal? = dates.firstNotNullOfOrNull { rateOn(it, fxPair) }

    private fun rateOn(
        dateStr: String,
        fxPair: IsoCurrencyPair
    ): BigDecimal? = fxCache[dateStr]?.rates?.get(fxPair)?.rate
}

/** One valuation date's prices and FX rates within a [HistoricalPrices] pass. */
class PricesOnDate internal constructor(
    private val dateStr: String,
    private val priceMap: Map<String, MarketData>,
    private val rateFor: (IsoCurrencyPair) -> BigDecimal?,
    private val latestPriceIndex: Map<String, MarketData>,
    private val boundary: TwrBoundary
) {
    private val cashUtils = CashUtils()

    /**
     * Total market value of [positions] in [currency]. For each position with
     * non-zero quantity: quantity * [unitValue]. Assets outside the TWR boundary are
     * left out; see [TwrBoundary.isExcluded].
     */
    fun marketValue(
        positions: Positions,
        currency: Currency
    ): BigDecimal =
        positions.positions.values
            .filter { it.quantityValues.getTotal().signum() != 0 && !boundary.isExcluded(it.asset) }
            .mapNotNull { pos -> unitValue(pos, currency)?.let { pos to it } }
            .fold(BigDecimal.ZERO) { total, (pos, unitValue) ->
                val positionMv = pos.quantityValues.getTotal().multiply(unitValue)
                total.add(if (cashUtils.isCash(pos.asset)) positionMv else positionMv.setScale(2, RoundingMode.HALF_UP))
            }

    /**
     * Value of one unit of [pos] in [currency]: price * fx(trade -> currency).
     * A cash unit is worth one of its currency, so needs no price. Null when a non-cash asset
     * has neither a price nor a cost basis to fall back on.
     */
    fun unitValue(
        pos: Position,
        currency: Currency
    ): BigDecimal? {
        val isCash = cashUtils.isCash(pos.asset)
        val price = if (isCash) BigDecimal.ONE else unitPrice(pos) ?: return null
        val tradeCcy = pos.moneyValues[Position.In.TRADE]?.currency ?: pos.asset.market.currency
        return if (tradeCcy.code == currency.code) {
            price
        } else {
            converted(price, IsoCurrencyPair(tradeCcy.code, currency.code))
        }
    }

    /**
     * [price] converted at the closest [fxPair] rate the pass holds. A pair with no rate
     * at all fails the pass: valuing the holding unconverted would report a wrong figure
     * as a measured one.
     */
    private fun converted(
        price: BigDecimal,
        fxPair: IsoCurrencyPair
    ): BigDecimal {
        val rate = rateFor(fxPair) ?: throw BusinessException("No FX rate for $fxPair as at $dateStr")
        return price.multiply(rate)
    }

    /**
     * Close on the date, else a PRIVATE asset's latest known price, else average cost.
     * A row whose close is not positive is no price, so it falls back the same way.
     */
    private fun unitPrice(pos: Position): BigDecimal? {
        val key = pos.key()
        val onDate = priceMap[key]?.takeIf { it.close.signum() > 0 }
        val latest = latestPriceIndex[key]
        return when {
            onDate != null -> {
                onDate.close
            }
            pos.asset.market.code == TwrBoundary.PRIVATE_MARKET && latest != null -> {
                // PRIVATE assets only ever get one price row (stamped today by
                // PrivateMarketDataProvider) — use it in preference to purchase
                // cost for every other valuation date.
                log.debug(
                    "No price for PRIVATE asset {} on {}, using latest known price {} from {}",
                    key,
                    dateStr,
                    latest.close,
                    latest.priceDate
                )
                latest.close
            }
            else -> {
                costPrice(pos, key)
            }
        }
    }

    /** Average cost when no market price is available; null when there is no cost basis either. */
    private fun costPrice(
        pos: Position,
        key: String
    ): BigDecimal? {
        val avgCost = pos.moneyValues[Position.In.TRADE]?.averageCost ?: BigDecimal.ZERO
        return if (avgCost.signum() == 0) {
            log.warn("No price or cost basis for {} on {}, skipping", key, dateStr)
            null
        } else {
            log.debug("No market price for {} on {}, using cost price {}", key, dateStr, avgCost)
            avgCost
        }
    }
}