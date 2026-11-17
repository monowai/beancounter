package com.beancounter.marketdata.broker.reconcile

import java.math.BigDecimal
import java.time.LocalDate

/**
 * When a holding was probably opened, worked out from its cost: the first day, oldest
 * first, the close came within [TOLERANCE] of the cost per share, or jumped across it.
 * Only a suggestion for the user to confirm: a position bought over time has an averaged
 * cost, so the first day near it can be earlier than the first purchase.
 */
internal object HeldSince {
    /** How near the cost, as a fraction of it, a close must be. */
    private val TOLERANCE = BigDecimal("0.10")

    /**
     * @property heldSince the suggested date, or null when there is none
     * @property before the earliest price, when the cost is below every price: the holding
     * was opened before it
     */
    data class Estimate(
        val heldSince: LocalDate? = null,
        val before: LocalDate? = null
    )

    /**
     * @param closes split-adjusted closing prices, in any order
     * @param costPerShare the holding's cost per share, on the same split basis
     */
    fun estimate(
        closes: List<Pair<LocalDate, BigDecimal>>,
        costPerShare: BigDecimal
    ): Estimate {
        if (closes.isEmpty() || costPerShare.signum() <= 0) return Estimate()
        val series = closes.sortedBy { it.first }
        val low = costPerShare - costPerShare * TOLERANCE
        val high = costPerShare + costPerShare * TOLERANCE
        val near =
            series.indices.firstOrNull { index ->
                val close = series[index].second
                val crossed =
                    index > 0 && (series[index - 1].second - costPerShare).signum() != (close - costPerShare).signum()
                close in low..high || crossed
            }
        return when {
            near != null -> Estimate(heldSince = series[near].first)
            series.first().second > high -> Estimate(before = series.first().first)
            else -> Estimate()
        }
    }
}