package com.beancounter.common.model

import java.math.BigDecimal

/**
 * External cash flows across a portfolio's boundary on one day: [inflow] the money in
 * (zero or more), [outflow] the money out, signed negative (zero or less).
 */
data class DayFlows(
    val inflow: BigDecimal = BigDecimal.ZERO,
    val outflow: BigDecimal = BigDecimal.ZERO
) {
    operator fun plus(other: DayFlows): DayFlows = DayFlows(inflow.add(other.inflow), outflow.add(other.outflow))

    /** Both amounts multiplied by [rate]. */
    fun times(rate: BigDecimal): DayFlows = DayFlows(inflow.multiply(rate), outflow.multiply(rate))

    fun isZero(): Boolean = inflow.signum() == 0 && outflow.signum() == 0

    companion object {
        val NONE = DayFlows()
    }
}