package com.beancounter.marketdata.broker.reconcile

import java.math.BigDecimal
import java.time.LocalDate

/**
 * When a holding was probably opened, worked out from its cost: the last day the price stood
 * at the cost per share. Only a suggestion for the user to confirm: a position bought over
 * time has an averaged cost, which leans the answer towards its most recent buys.
 */
internal object HeldSince {
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
        val risen = series.last().second >= costPerShare
        // The last day the price was on the far side of the cost from where it is now.
        val crossed =
            series.lastOrNull { (_, close) ->
                if (risen) close <= costPerShare else close >= costPerShare
            }
        return if (crossed != null) Estimate(heldSince = crossed.first) else Estimate(before = series.first().first)
    }
}