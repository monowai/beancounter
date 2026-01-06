package com.beancounter.common.model

import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.PortfolioUtils
import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.time.LocalDate
import java.util.EnumMap
import java.util.TreeMap

/**
 * A container for Position objects. Position will be added when requested.
 * <pre>
 *  val positions = Positions(getPortfolio())
 *  val buyTrn = Trn(
 *     TrnType.BUY, getAsset(Market("marketCode"), "CODE"),
 *     quantity = hundred,
 *     tradeAmount = BigDecimal(2000)
 *  )
 *  assertThat(accumulator.accumulate(buyTrn, positions).quantityValues)
 *   .hasFieldOrPropertyWithValue(totalProp, hundred)
 *   .hasFieldOrPropertyWithValue(purchasedProp, hundred)
 *</pre>
 *
 * @author mikeh
 * @since 2019-02-07
 */
class Positions(
    val portfolio: Portfolio = PortfolioUtils.getPortfolio(),
    var asAt: String = DEFAULT_AS_AT,
    val positions: MutableMap<String, Position> = TreeMap(),
    val totals: MutableMap<Position.In, Totals> = EnumMap(Position.In::class.java)
) {
    companion object {
        const val DEFAULT_AS_AT = "today"
        private const val CASH_MARKET = "CASH"
        private const val PRIVATE_MARKET = "PRIVATE"

        /**
         * Get the appropriate trade currency for an asset.
         * - For CASH/PRIVATE markets: use priceSymbol which stores the currency
         * - For other markets: use the market's default currency
         */
        fun getTradeCurrency(asset: Asset): Currency =
            when (asset.market.code) {
                CASH_MARKET, PRIVATE_MARKET -> {
                    if (asset.priceSymbol != null) {
                        Currency(asset.priceSymbol!!)
                    } else {
                        asset.market.currency
                    }
                }
                else -> asset.market.currency
            }
    }

    @JsonIgnore
    val periodicCashFlows = PeriodicCashFlows()

    var isMixedCurrencies = false

    @JsonIgnore
    private val cashUtils: CashUtils = CashUtils()

    @JsonIgnore
    private val tradeCurrencies: MutableCollection<Currency> = mutableListOf()

    fun add(position: Position): Position {
        if (cashUtils.isCash(position.asset)) {
            position.quantityValues.setPrecision(2)
        }
        positions[toKey(position.asset)] = position
        updateMixedCurrenciesFlag(position)
        return position
    }

    private fun updateMixedCurrenciesFlag(position: Position) {
        if (!isMixedCurrencies) {
            val tradeCurrency = getTradeCurrency(position.asset)
            if (!tradeCurrencies.contains(tradeCurrency)) {
                tradeCurrencies.add(tradeCurrency)
            }
            isMixedCurrencies = tradeCurrencies.size > 1
        }
    }

    /**
     * Get an existing position or create a new one without setting the opened date.
     * This overload is used during valuation where we don't want to affect date values.
     * Use getOrCreate(Trn) or getOrCreate(Asset, LocalDate) when processing transactions.
     */
    @JsonIgnore
    fun getOrCreate(asset: Asset): Position {
        val position =
            positions[toKey(asset)] ?: add(
                Position(
                    asset,
                    portfolio,
                    getTradeCurrency(asset)
                )
            )
        return position
    }

    @JsonIgnore
    operator fun contains(asset: Asset) = positions.contains(toKey(asset))

    /**
     * Get or create a position for an asset. Used for cash positions and other
     * non-transaction-based position creation where reopening detection is not needed.
     */
    @JsonIgnore
    fun getOrCreate(
        asset: Asset,
        tradeDate: LocalDate,
        tradeCurrency: Currency = getTradeCurrency(asset)
    ): Position {
        val firstTrade = !positions.containsKey(toKey(asset))
        val position =
            positions[toKey(asset)] ?: add(
                Position(
                    asset,
                    portfolio,
                    tradeCurrency
                )
            )

        // Set opened date if first trade
        if (firstTrade) {
            position.dateValues.opened = tradeDate
        }

        return position
    }

    fun hasPositions() = positions.isNotEmpty()

    fun setTotal(
        valueIn: Position.In,
        totals: Totals
    ) {
        this.totals[valueIn] = totals
    }

    /**
     * This is the preferred way to getOrCreate a Position from a transaction.
     */
    @JsonIgnore
    fun getOrCreate(trn: Trn): Position {
        val firstTrade = !positions.containsKey(toKey(trn.asset))
        val position =
            positions[toKey(trn.asset)] ?: add(
                Position(
                    trn.asset,
                    portfolio,
                    trn.tradeCurrency
                )
            )

        // Detect if we're re-entering a sold-out position
        // A position is "reopening" if:
        // 1. It's not a first trade (position already exists)
        // 2. It has zero quantity
        // 3. It had actual transactions (firstTransaction is set by Accumulator)
        // 4. The transaction is a position-building type (BUY, not DIVI/SPLIT etc.)
        //    Note: DIVIs can arrive after a position is sold out (ex-dividend dates)
        //    and should not trigger a "reopen" with cash flow clearing
        val hasZeroQuantity = position.quantityValues.getTotal() == BigDecimal.ZERO
        val hadActualTransactions = position.dateValues.firstTransaction != null
        val isPositionBuildingTrn = TrnType.isPositionBuilding(trn.trnType)
        val isReopening = !firstTrade && hasZeroQuantity && hadActualTransactions && isPositionBuildingTrn

        // Set opened date if first trade OR if re-entering after selling out completely
        if (firstTrade || isReopening) {
            position.dateValues.opened = trn.tradeDate
        }

        // When reopening after a complete sell-out, clear historical cash flows
        // This starts a new investment cycle - historical returns are realized and not relevant
        // for the new position's ROI/XIRR calculation
        // Note: firstTransaction is NOT reset - it tracks the very first transaction ever for this asset
        if (isReopening) {
            position.periodicCashFlows.clear()
        }

        return position
    }

    fun getOrThrow(asset: Asset): Position =
        positions[toKey(asset)] ?: throw IllegalArgumentException("No position for $asset")
}