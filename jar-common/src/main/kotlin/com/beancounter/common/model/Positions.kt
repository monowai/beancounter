package com.beancounter.common.model

import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.PortfolioUtils
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate
import java.util.EnumMap
import java.util.TreeMap

/**
 * A container for Position objects.
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

    var isMixedCurrencies = false

    @JsonIgnore
    private val tradeCurrencies: MutableCollection<Currency> = ArrayList()

    fun add(position: Position) {
        positions[toKey(position.asset)] = position
        if (!isMixedCurrencies) {
            if (!tradeCurrencies.contains(position.asset.market.currency)) {
                tradeCurrencies.add(position.asset.market.currency)
            }
            if (tradeCurrencies.size != 1) {
                isMixedCurrencies = true
            }
        }
    }

    /**
     * Locate a position for an asset. Creates if missing.
     *
     * @param asset qualified asset
     * @return value if found.
     */
    @JsonIgnore
    operator fun get(asset: Asset?): Position {
        val result = positions[toKey(asset!!)]
        return result ?: Position(asset)
    }

    @JsonIgnore
    operator fun get(asset: Asset?, tradeDate: LocalDate?): Position {
        val firstTrade = !positions.containsKey(toKey(asset!!))
        val position = get(asset)
        if (firstTrade) {
            position.dateValues.opened = tradeDate
        }
        return position
    }

    fun hasPositions(): Boolean {
        return positions.isNotEmpty()
    }

    fun setTotal(valueIn: Position.In, totals: Totals) {
        this.totals[valueIn] = totals
    }
}
