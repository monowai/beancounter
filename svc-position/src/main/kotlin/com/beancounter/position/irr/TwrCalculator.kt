package com.beancounter.position.irr

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A valuation snapshot at a point in time, optionally with an external cash flow.
 *
 * @param date the valuation date
 * @param marketValue total portfolio market value on this date (after any cash flow)
 * @param externalCashFlow net external cash flow on this date (positive=deposit, negative=withdrawal)
 */
data class ValuationSnapshot(
    val date: LocalDate,
    val marketValue: BigDecimal,
    val externalCashFlow: BigDecimal = BigDecimal.ZERO
)

/**
 * Calculates Time-Weighted Return (TWR) using the chain-linking method.
 *
 * TWR measures portfolio performance independent of external cash flows (deposits/withdrawals),
 * making it the GIPS-compliant standard for comparing fund managers.
 *
 * For each sub-period between cash flows:
 *   r_i = (V_end - CF_end) / V_start
 * where V_end is the portfolio value (after cash flow), CF_end is the cash flow,
 * and V_start is the previous portfolio value (after its cash flow).
 *
 * TWR = product of (1 + r_i) - 1
 */
@Service
class TwrCalculator {
    companion object {
        private val SCALE = 10
        private val MC = MathContext(15, RoundingMode.HALF_UP)
        private val ONE_THOUSAND = BigDecimal("1000")
    }

    /**
     * Calculate the cumulative TWR from a series of valuation snapshots.
     *
     * @param snapshots ordered by date, each with market value and optional external cash flow
     * @return cumulative TWR as a decimal (0.15 = 15% return)
     */
    fun calculate(snapshots: List<ValuationSnapshot>): BigDecimal {
        if (snapshots.size < 2) return BigDecimal.ZERO

        var twrFactor = BigDecimal.ONE
        for (i in 1 until snapshots.size) {
            val vStart = snapshots[i - 1].marketValue
            if (vStart.compareTo(BigDecimal.ZERO) == 0) continue

            // Value before the cash flow at the end of this sub-period
            val vEndBeforeCf = snapshots[i].marketValue - snapshots[i].externalCashFlow
            val subPeriodReturn = vEndBeforeCf.divide(vStart, SCALE, RoundingMode.HALF_UP)
            twrFactor = twrFactor.multiply(subPeriodReturn, MC)
        }

        return twrFactor.subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP)
    }

    /**
     * Calculate a Growth of $1,000 series from valuation snapshots.
     *
     * @param snapshots ordered by date
     * @return list of growth values, one per snapshot (starts at 1000)
     */
    fun calculateSeries(snapshots: List<ValuationSnapshot>): List<BigDecimal> {
        if (snapshots.isEmpty()) return emptyList()
        if (snapshots.size == 1) return listOf(ONE_THOUSAND)

        val series = mutableListOf(ONE_THOUSAND)
        var twrFactor = BigDecimal.ONE

        for (i in 1 until snapshots.size) {
            val vStart = snapshots[i - 1].marketValue
            if (vStart.compareTo(BigDecimal.ZERO) == 0) {
                series.add(ONE_THOUSAND)
                continue
            }

            val vEndBeforeCf = snapshots[i].marketValue - snapshots[i].externalCashFlow
            val subPeriodReturn = vEndBeforeCf.divide(vStart, SCALE, RoundingMode.HALF_UP)
            twrFactor = twrFactor.multiply(subPeriodReturn, MC)
            series.add(ONE_THOUSAND.multiply(twrFactor, MC).setScale(2, RoundingMode.HALF_UP))
        }

        return series
    }
}