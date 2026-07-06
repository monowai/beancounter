package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnGroupBy
import com.beancounter.common.contracts.TrnGroupTotal
import com.beancounter.common.contracts.TrnTradeSummary
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import java.math.BigDecimal

/**
 * Builds a split-adjusted quantity summary for an asset's trade drill-down.
 *
 * The drill-down groups an asset's trns by broker (single portfolio) or by
 * portfolio (aggregated view). Summing raw `trn.quantity` is wrong: a SPLIT row
 * holds the ratio (e.g. 4 = 4:1), so it must MULTIPLY the running holding, not
 * add. A SPLIT is a corporate action on the whole position, so it is applied
 * portfolio-wide to every broker group in that portfolio — mirroring the
 * broker-holdings path, which re-merges broker-null splits into each broker's
 * set (see [TrnBrokerService]).
 */
object TrnTradeSummaryBuilder {
    // Same-day ordering: buys/adds, then the split, then sells/reduces, then a
    // BALANCE (an absolute end-of-day position snapshot) which resets the total.
    private fun sameDayOrder(type: TrnType): Int =
        when (type) {
            TrnType.BUY, TrnType.ADD -> 0
            TrnType.SPLIT -> 1
            TrnType.BALANCE -> 3
            else -> 2
        }

    fun build(
        trns: Collection<Trn>,
        groupBy: TrnGroupBy
    ): TrnTradeSummary {
        val keyOf: (Trn) -> String = { trn ->
            when (groupBy) {
                TrnGroupBy.BROKER -> trn.broker?.id ?: ""
                TrnGroupBy.PORTFOLIO -> trn.portfolio.id
            }
        }
        // A split is portfolio-wide; index them so every group in the portfolio
        // scales by it, even brokers whose own rows carry no split.
        val splitsByPortfolio =
            trns
                .filter { it.trnType == TrnType.SPLIT }
                .groupBy { it.portfolio.id }

        val groups =
            trns.groupBy(keyOf).map { (groupId, groupTrns) ->
                val portfolioIds = groupTrns.map { it.portfolio.id }.toSet()
                val splitEvents = portfolioIds.flatMap { splitsByPortfolio[it].orEmpty() }
                // Fold the group's own non-split trns plus the portfolio's splits.
                val events = groupTrns.filter { it.trnType != TrnType.SPLIT } + splitEvents
                var quantity = BigDecimal.ZERO
                for (trn in events.sortedWith(
                    compareBy({ it.tradeDate }, { sameDayOrder(it.trnType) })
                )) {
                    quantity =
                        when (trn.trnType) {
                            TrnType.BUY, TrnType.ADD -> quantity.add(trn.quantity)
                            TrnType.SELL, TrnType.REDUCE -> quantity.subtract(trn.quantity)
                            TrnType.SPLIT -> quantity.multiply(trn.quantity)
                            TrnType.BALANCE -> trn.quantity
                            else -> quantity
                        }
                }
                TrnGroupTotal(groupId, quantity)
            }
        return TrnTradeSummary(groupBy, groups)
    }
}