package com.beancounter.common.model

import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.PortfolioUtils
import com.fasterxml.jackson.annotation.JsonIgnore
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
    var asAt: String = "today",
    val positions: MutableMap<String, Position> = TreeMap(),
    val totals: MutableMap<Position.In, Totals> = EnumMap(Position.In::class.java)
) {
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
            if (!tradeCurrencies.contains(position.asset.market.currency)) {
                tradeCurrencies.add(position.asset.market.currency)
            }
            if (tradeCurrencies.size != 1) {
                isMixedCurrencies = true
            }
        }
    }

    @JsonIgnore
    fun getOrCreate(asset: Asset): Position =
        getOrCreate(
            asset,
            LocalDate.now()
        )

    @JsonIgnore
    operator fun contains(asset: Asset) = positions.contains(toKey(asset))

    @JsonIgnore
    fun getOrCreate(
        asset: Asset,
        tradeDate: LocalDate,
        tradeCurrency: Currency = asset.market.currency
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
     * This is the preferred way to getOrCreate a Position.
     */
    @JsonIgnore
    fun getOrCreate(trn: Trn): Position =
        getOrCreate(
            trn.asset,
            trn.tradeDate,
            trn.tradeCurrency
        )
}