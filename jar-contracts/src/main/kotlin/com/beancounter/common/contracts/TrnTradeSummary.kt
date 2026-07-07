package com.beancounter.common.contracts

import java.math.BigDecimal

/** Dimension a trade drill-down is grouped by when summarising quantity held. */
enum class TrnGroupBy {
    BROKER,
    PORTFOLIO
}

/**
 * Split-adjusted quantity held for one group of an asset's trades.
 *
 * [groupId] is the broker id (empty string for "no broker") when the summary
 * groups by [TrnGroupBy.BROKER], or the portfolio id when it groups by
 * [TrnGroupBy.PORTFOLIO].
 *
 * [subTotals] breaks a portfolio group down by broker (empty for broker
 * grouping and for portfolios that never carried a broker). Each sub-total is
 * split-adjusted with the portfolio-wide split applied, so summing the
 * sub-totals reconstructs [quantity].
 */
data class TrnGroupTotal(
    val groupId: String,
    val quantity: BigDecimal,
    val subTotals: List<TrnGroupTotal> = emptyList()
)

/**
 * Server-computed, split-adjusted quantity per group for a trade drill-down.
 *
 * The UI groups an asset's trns by broker (single portfolio) or by portfolio
 * (aggregated view) and reads the authoritative quantity from here rather than
 * summing raw `trn.quantity` — a SPLIT row carries the ratio, not shares.
 */
data class TrnTradeSummary(
    val groupBy: TrnGroupBy,
    val groups: List<TrnGroupTotal> = emptyList()
)